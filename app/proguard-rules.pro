# Keep kotlinx.serialization metadata for TMDB DTOs
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.matanjerbi.cinematheque.**$$serializer { *; }
-keepclassmembers class com.matanjerbi.cinematheque.** {
    *** Companion;
}
