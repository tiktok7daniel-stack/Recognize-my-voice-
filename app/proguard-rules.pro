# Personal app: shrink only, never obfuscate (keeps reflection-based libraries and stack traces sane).
-dontobfuscate
-ignorewarnings

# Vosk / JNA (native bridge uses reflection)
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**

# Anthropic SDK + Jackson (reflection-based JSON)
# Keep only the parts of the (very large) SDK that Nabra uses: core, OkHttp client, beta messages.
-keep class com.anthropic.core.** { *; }
-keep class com.anthropic.client.** { *; }
-keep class com.anthropic.errors.** { *; }
-keep class com.anthropic.services.blocking.** { *; }
-keep class com.anthropic.models.beta.messages.** { *; }
-keep class com.anthropic.models.messages.** { *; }
-keep class com.anthropic.models.beta.AnthropicBeta { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisible*Annotations,KotlinMetadata

# MediaPipe on-device LLM
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }

# kotlinx.serialization (our @Serializable models)
-keepclassmembers class com.daniel.assistant.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.daniel.assistant.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
