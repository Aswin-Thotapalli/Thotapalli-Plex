# R8 / ProGuard rules for the Thotapalli Plex Android TV & Google TV release.
#
# The heavy lifting is done by the consumer rules the libraries ship — Media3, Ktor, OkHttp, Coil,
# kotlinx.serialization and Compose each bundle their own -keep set, applied automatically. What
# follows covers the two things R8 cannot see on its own: reflection into this app's own code, and
# the FFmpeg extension renderer Media3 loads by name.

# --- kotlinx.serialization ------------------------------------------------------------------------
# The @Serializable DTOs in core:api are serialised through generated $$serializer classes and a
# Companion.serializer() accessor, both reached reflectively. The plugin's consumer rules already
# keep these, but the app's DTOs live in a library module, so this restates them for this app's
# package to be certain a mapper never meets a stripped serializer at runtime.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class com.thotapalli.plex.**$$serializer { *; }
-keep,includedescriptorclasses class com.thotapalli.plex.**$$serializer { *; }
-keepclassmembers class com.thotapalli.plex.** {
    *** Companion;
}
-keepclasseswithmembers class com.thotapalli.plex.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# The domain model and DTOs carry no code of their own worth stripping and are safest kept whole,
# since serialization and the Plex mappers both reach their fields.
-keep class com.thotapalli.plex.core.model.** { *; }
-keep class com.thotapalli.plex.core.api.dto.** { *; }

# --- Media3 FFmpeg extension renderer -------------------------------------------------------------
# DefaultRenderersFactory (EXTENSION_RENDERER_MODE_PREFER) finds the bundled FFmpeg audio decoder by
# reflecting on its class name; stripping or renaming it would silently fall back to a server
# transcode for audio the device cannot decode. Keep the decoder and its JNI entry points.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclasseswithmembernames class androidx.media3.decoder.ffmpeg.** {
    native <methods>;
}

# --- Coroutines / atomics -------------------------------------------------------------------------
# Volatile field updaters are resolved reflectively by name inside kotlinx.coroutines.
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
