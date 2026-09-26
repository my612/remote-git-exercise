# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.mobuk.app.**$$serializer { *; }
-keepclassmembers class com.mobuk.app.** { *** Companion; }
-keepclasseswithmembers class com.mobuk.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**

# LiteRT-LM and ML Kit ship native code and their own consumer rules.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.mlkit.genai.** { *; }
