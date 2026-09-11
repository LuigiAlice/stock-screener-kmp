# Shrinking only: keine Obfuskierung/Optimierung (Compose/Swing sind da empfindlich)
-dontoptimize
-dontobfuscate
-dontwarn **

# Einstiegspunkt
-keep class de.luigialice.stockscreener.MainKt {
    public static void main(java.lang.String[]);
}

# Skiko/Skia: natives JNI-Gegenstück, Namen und Klassen müssen bleiben
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }

# JNA (von Skiko für Fenster-Integration genutzt, nutzt Reflection)
-keep class com.sun.jna.** { *; }
-keep class com.sun.jna.ptr.** { *; }

# Kotlin-Metadaten und Annotations für Runtime-Reflexion
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations,AnnotationDefault
-keep class kotlin.Metadata { *; }

# ServiceLoader-Dienste nicht umbenennen (Namen bleiben ohnehin durch -dontobfuscate stabil)
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }
