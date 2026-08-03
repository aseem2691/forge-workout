# kotlinx.serialization keeps its generated serializers via @Serializable companions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.forge.workout.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.forge.workout.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
