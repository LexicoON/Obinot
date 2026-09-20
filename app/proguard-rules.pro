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

# ---------- Moshi ----------
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
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ---------- Jetpack Compose ----------
-dontwarn androidx.compose.**

# ---------- WebView (Mermaid) ----------
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn android.webkit.**
-dontwarn org.chromium.**

# ---------- MaterialKolor ----------
-dontwarn com.materialkolor.**

# ---------- Serialización genérica ----------
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---------- Firebase AI ----------
-dontwarn com.google.firebase.**

# ---------- RaTeX (LaTeX renderer) ----------
# RaTeX usa JNI para llamar al core de Rust. R8 no ve esas llamadas
# porque se hacen desde el lado nativo, así que hay que preservar
# todas las clases del paquete ratex y sus miembros.
-keep class io.github.erweixin.ratex.** { *; }
-keepclassmembers class io.github.erweixin.ratex.** { *; }
-dontwarn io.github.erweixin.ratex.**

# ---------- Reglas específicas de la app ----------
-keep class com.obinot.app.CrashActivity { *; }
-keep class com.obinot.app.MainActivity { *; }
-keep class com.obinot.app.BinotApplication { *; }
-keep class com.obinot.app.utils.RecordingService { *; }