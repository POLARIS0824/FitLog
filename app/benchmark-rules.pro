# Instrumentation and the offline UI replay address these classes directly.
-keep class com.example.fitlog.benchmark.** { *; }

# Same serialization reflection requirements as the production build.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> { static <1>$Companion Companion; }
-if @kotlinx.serialization.Serializable class ** { static **$* *; }
-keepclassmembers class <2>$<3> { kotlinx.serialization.KSerializer serializer(...); }
-keepclasseswithmembers class **$$serializer { *** INSTANCE; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# GenAI 1.4's unused Google HTTP client references the pre-3.4 Ktor timeout ABI.
# Our custom ADK Model uses FitLog's transport. Fail the build if that client survives R8.
-checkdiscard class com.google.genai.kotlin.ApiClient
-dontwarn io.ktor.client.plugins.HttpTimeout
-dontwarn io.ktor.client.plugins.HttpTimeout$HttpTimeoutCapabilityConfiguration
-dontwarn io.ktor.client.plugins.HttpTimeout$Plugin
