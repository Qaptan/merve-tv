# kotlinx.serialization keeps generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class tr.merve.tv.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class tr.merve.tv.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
