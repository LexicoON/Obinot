# ============================================================
# Obinot — ProGuard / R8 rules
# ============================================================

# ---------- Atributos generales ----------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- Kotlin metadata ----------
# Necesario para que reflection sobre data classes / sealed classes funcione.
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ---------- Kotlin Coroutines ----------
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---------- Moshi (usa reflection via KotlinJsonAdapterFactory) ----------
# Los data classes de `data/` son reflejados por Moshi en runtime, así que
# sus constructores, properties y campos deben sobrevivir el minify.
-keep class com.obinot.app.data.** { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-dontwarn javax.annotation.**

# ---------- OkHttp / Retrofit ----------
-keep class retrofit2.** { *; }
-keepclassmembers class retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ---------- Room ----------
# Room genera clases _Impl en tiempo de compilación. Las reglas de retención
# las genera el plugin automáticamente, pero por las dudas:
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ---------- Jetpack Compose ----------
# El compilador de Compose maneja todo, pero por las dudas:
-dontwarn androidx.compose.**

# ---------- WebView JavascriptInterface ----------
# Las clases anónimas que exponemos con addJavascriptInterface deben
# mantener sus métodos anotados con @JavascriptInterface, o R8 los
# renombra y JS no puede encontrarlos.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    public *;
    @android.webkit.JavascriptInterface *;
}

# ---------- WebView internals ----------
# Algunas versiones del WebView de Google tienen referencias que R8 no
# puede resolver. Silenciamos los warnings.
-dontwarn android.webkit.**
-dontwarn org.chromium.**

# ---------- MaterialKolor ----------
-dontwarn com.materialkolor.**

# ---------- Serialización genérica ----------
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---------- Firebase AI ----------
-dontwarn com.google.firebase.**

# ---------- Reglas específicas de la app ----------
# El CrashActivity se lanza vía Intent con el nombre de clase. NO debe
# ser renombrado.
-keep class com.obinot.app.CrashActivity { *; }
-keep class com.obinot.app.MainActivity { *; }
-keep class com.obinot.app.BinotApplication { *; }
-keep class com.obinot.app.utils.RecordingService { *; }