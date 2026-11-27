package com.devlink42.cinepathe.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.devlink42.cinepathe.data.model.BookingOrderDto
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

@Serializable
data class SeatAllocationRequest(
    val seats: List<SeatPayload>,
    val activeShowing: Int
)

@Serializable
data class SeatPayload(
    val rowIndex: Int,
    val rowName: String,
    val seatIndex: Int,
    val seatName: String,
    val seatSide: String? = null,
    val seatType: String = "STD",
    val section: Int = 1
)

class PatheBookingService(
    private val client: HttpClient,
    private val context: Context? = null,
    private val authManager: PatheAuthManager? = null
) {
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingFetches = ConcurrentHashMap<String, CompletableDeferred<Pair<Int, String>>>()

    @Volatile
    private var activeWebView: WebView? = null

    @Volatile
    private var capturedAuthHeader: String? = null

    private inner class BookingJsBridge {
        @JavascriptInterface
        fun onFetchResult(requestId: String, status: Int, body: String) {
            pendingFetches.remove(requestId)?.complete(status to body)
        }

        @JavascriptInterface
        fun onAuthCaptured(authHeader: String) {
            if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                capturedAuthHeader = authHeader
            }
        }
    }

    suspend fun startBookingFromRefCmd(
        fallbackCinemaId: Int,
        refCmd: String
    ): Triple<Int, Int, BookingOrderDto> {
        val vsMatch = Regex("""V(\d+)S(\d+)""").find(refCmd)
        val resolvedCinemaId = vsMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: fallbackCinemaId
        val resolvedShowingId = vsMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: Regex("""\d+""").findAll(refCmd).lastOrNull()?.value?.toIntOrNull()
            ?: 0

        val fullRefUrl = when {
            refCmd.startsWith("http") -> refCmd
            refCmd.startsWith("/") -> "https://s.pathe.fr$refCmd"
            else -> "https://s.pathe.fr/$refCmd"
        }

        val (cinemaId, orderId) = resolveOrderViaWebView(fullRefUrl, resolvedCinemaId)
            ?: error("Délai dépassé lors de l'initialisation de la séance sur s.pathe.fr")

        val bookingOrder = getBookingOrder(cinemaId, orderId)

        if (resolvedShowingId > 0 && activeWebView != null) {
            val (mapStatus, mapRaw) = fetchViaWebView(
                "https://s.pathe.fr/api/seatmap/fr-FR/$cinemaId/$resolvedShowingId/map"
            )
            Log.d("PatheSeatMap", "MAP ($mapStatus): ${mapRaw.take(1500)}")

            val (seatingStatus, seatingRaw) = fetchViaWebView(
                "https://s.pathe.fr/api/order/fr-FR/$cinemaId/$resolvedShowingId/seating"
            )
            Log.d("PatheSeatMap", "SEATING ($seatingStatus): ${seatingRaw.take(1500)}")
        }

        return Triple(cinemaId, resolvedShowingId, bookingOrder)
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private suspend fun resolveOrderViaWebView(
        bookingUrl: String,
        defaultCinemaId: Int
    ): Pair<Int, String>? = withTimeoutOrNull(15_000L) {
        val appContext = context?.applicationContext ?: return@withTimeoutOrNull null

        destroyActiveWebView()
        capturedAuthHeader = null

        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                val webView = WebView(appContext)
                cookieManager.setAcceptThirdPartyCookies(webView, true)

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                webView.addJavascriptInterface(BookingJsBridge(), "AndroidBookingBridge")
                activeWebView = webView

                val apiOrderRegex = Regex("""/order/(\d+)/([a-zA-Z0-9-]{8,})""")
                val tokenRegex = Regex("""[?&]token=([a-zA-Z0-9-]{8,})""")

                val hookScript = """
                    (function() {
                        if (window.__patheHookInstalled) return;
                        window.__patheHookInstalled = true;
                        window.__patheAuthHeader = window.__patheAuthHeader || null;
                        const origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
                        XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
                            if (name && name.toLowerCase() === 'authorization' && value) {
                                window.__patheAuthHeader = value;
                                if (window.AndroidBookingBridge) {
                                    window.AndroidBookingBridge.onAuthCaptured(value);
                                }
                            }
                            return origSetHeader.apply(this, arguments);
                        };
                    })();
                """.trimIndent()

                fun tryExtractAndFinish(url: String) {
                    if (!continuation.isActive) return

                    val apiMatch = apiOrderRegex.find(url)
                    val tokenMatch = tokenRegex.find(url)

                    val cId = apiMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: defaultCinemaId
                    val oId = apiMatch?.groupValues?.getOrNull(2)
                        ?: tokenMatch?.groupValues?.getOrNull(1)
                        ?: return

                    if (oId.equals("booking", ignoreCase = true) || oId.equals("start", ignoreCase = true)) {
                        return
                    }

                    cookieManager.flush()
                    if (continuation.isActive) {
                        continuation.resume(cId to oId)
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view?.evaluateJavascript(hookScript, null)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString().orEmpty()
                        if (reqUrl.contains("/order/") || reqUrl.contains("token=")) {
                            mainHandler.post { tryExtractAndFinish(reqUrl) }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        view?.evaluateJavascript(hookScript, null)
                        if (!url.isNullOrBlank()) {
                            tryExtractAndFinish(url)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(hookScript, null)
                        if (!url.isNullOrBlank()) {
                            tryExtractAndFinish(url)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post { destroyWebViewInternal() }
                }

                webView.onResume()
                webView.resumeTimers()
                webView.loadUrl(bookingUrl)
            }
        }
    }

    private suspend fun resolveBearerHeader(): String? {
        capturedAuthHeader?.let { return it }
        val token = runCatching { authManager?.getValidAccessToken() }.getOrNull()
        return token?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    }

    private suspend fun fetchViaWebView(
        url: String,
        method: String = "GET",
        jsonBody: String? = null
    ): Pair<Int, String> {
        val wv = activeWebView ?: error("Session WebView expirée.")
        val reqId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Pair<Int, String>>()
        pendingFetches[reqId] = deferred

        val fallbackAuth = resolveBearerHeader()
        val quotedUrl = JSONObject.quote(url)
        val quotedMethod = JSONObject.quote(method)
        val quotedReqId = JSONObject.quote(reqId)
        val quotedFallbackAuth = if (fallbackAuth != null) JSONObject.quote(fallbackAuth) else "null"
        val bodyJs = if (jsonBody != null) JSONObject.quote(jsonBody) else "null"
        val hasBody = jsonBody != null

        val script = """
            (async function() {
                try {
                    let auth = window.__patheAuthHeader || $quotedFallbackAuth;
                    if (!auth) {
                        for (let i = 0; i < localStorage.length; i++) {
                            const k = localStorage.key(i);
                            if (k && k.indexOf('auth0spajs') !== -1) {
                                try {
                                    const parsed = JSON.parse(localStorage.getItem(k));
                                    if (parsed && parsed.body && parsed.body.access_token) {
                                        auth = 'Bearer ' + parsed.body.access_token;
                                        window.__patheAuthHeader = auth;
                                        break;
                                    }
                                } catch (_) {}
                            }
                        }
                    }
                    const headers = { "Accept": "application/json, text/plain, */*" };
                    if ($hasBody) {
                        headers["Content-Type"] = "application/json";
                    }
                    if (auth) {
                        headers["Authorization"] = auth;
                    }
                    const opts = {
                        method: $quotedMethod,
                        credentials: 'include',
                        headers: headers
                    };
                    const payload = $bodyJs;
                    if (payload !== null) {
                        opts.body = payload;
                    }
                    const resp = await fetch($quotedUrl, opts);
                    const text = await resp.text();
                    AndroidBookingBridge.onFetchResult($quotedReqId, resp.status, text);
                } catch (e) {
                    AndroidBookingBridge.onFetchResult($quotedReqId, -1, String(e));
                }
            })();
        """.trimIndent()

        try {
            withContext(Dispatchers.Main) {
                wv.evaluateJavascript(script, null)
            }
            return withTimeout(15_000L) {
                deferred.await()
            }
        } finally {
            pendingFetches.remove(reqId)
        }
    }

    suspend fun getBookingOrder(cinemaId: Int, orderId: String): BookingOrderDto {
        val url = "https://s.pathe.fr/api/fr-FR/order/$cinemaId/$orderId/booking"
        var (status, raw) = if (activeWebView != null) {
            fetchViaWebView(url)
        } else {
            val response: HttpResponse = client.get(url) {
                header("Accept", "application/json, text/plain, */*")
                header("Origin", "https://s.pathe.fr")
                header("Referer", "https://s.pathe.fr/")
                resolveBearerHeader()?.let { header("Authorization", it) }
            }
            response.status.value to response.bodyAsText()
        }

        if (status == 401 && activeWebView != null) {
            delay(400L)
            val retry = fetchViaWebView(url)
            status = retry.first
            raw = retry.second
        }

        Log.d("PatheSeatMap", "BOOKING ($status): ${raw.take(1500)}")
        if (status !in 200..299) {
            error("Erreur serveur Pathé ($status) lors du chargement de la salle.")
        }
        return jsonParser.decodeFromString(raw)
    }

    suspend fun allocateSeats(
        cinemaId: Int,
        showingId: Int,
        orderId: String,
        seats: List<SeatPayload>
    ): Boolean {
        val url = "https://s.pathe.fr/api/fr-FR/order/$cinemaId/$orderId/seat/allocation?pos=c2VhdGluZw=="
        val payloadJson = jsonParser.encodeToString(
            SeatAllocationRequest(
                seats = seats,
                activeShowing = showingId
            )
        )

        val (status, raw) = if (activeWebView != null) {
            fetchViaWebView(url, method = "POST", jsonBody = payloadJson)
        } else {
            val response: HttpResponse = client.post(
                "https://s.pathe.fr/api/fr-FR/order/$cinemaId/$orderId/seat/allocation"
            ) {
                parameter("pos", "c2VhdGluZw==")
                header("Origin", "https://s.pathe.fr")
                header("Referer", "https://s.pathe.fr/")
                resolveBearerHeader()?.let { header("Authorization", it) }
                contentType(ContentType.Application.Json)
                setBody(payloadJson)
            }
            response.status.value to response.bodyAsText()
        }

        Log.d("PatheBooking", "POST seat/allocation status=$status body=${raw.take(300)}")
        return status in 200..299
    }

    suspend fun cancelOrder(cinemaId: Int, orderId: String): Boolean {
        val url = "https://s.pathe.fr/api/fr-FR/order/$cinemaId/$orderId"
        val result = if (activeWebView != null) {
            val (status, _) = runCatching { fetchViaWebView(url, method = "DELETE") }
                .getOrDefault(-1 to "")
            status in 200..299
        } else {
            val response: HttpResponse = client.delete(url) {
                header("Origin", "https://s.pathe.fr")
                header("Referer", "https://s.pathe.fr/")
                resolveBearerHeader()?.let { header("Authorization", it) }
            }
            response.status.isSuccess()
        }
        destroyActiveWebView()
        return result
    }

    suspend fun destroyActiveWebView() {
        withContext(Dispatchers.Main) {
            destroyWebViewInternal()
        }
    }

    private fun destroyWebViewInternal() {
        pendingFetches.values.forEach { it.cancel() }
        pendingFetches.clear()
        activeWebView?.let { wv ->
            runCatching {
                wv.stopLoading()
                wv.removeJavascriptInterface("AndroidBookingBridge")
                wv.destroy()
            }
        }
        activeWebView = null
        capturedAuthHeader = null
    }
}
