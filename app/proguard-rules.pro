# --- KOTLINX SERIALIZATION (Garde les modèles JSON intacts) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt

-keep,includedescriptorclasses class com.tonpackage.pathelight.**$$serializer { *; }
-keepclassmembers class com.tonpackage.pathelight.** {
    *** Companion;
}
-keepclasseswithmembers class com.tonpackage.pathelight.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- KTOR & OKHTTP ---
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**

# --- JAVASCRIPT INTERFACE (Auth0 Silent Refresh) ---
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
