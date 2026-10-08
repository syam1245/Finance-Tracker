# Add project specific ProGuard rules here.

# Keep kotlinx.serialization data models and serializers
-keep class com.naseeb.salestracker.data.model.** { *; }
-keepclassmembers class com.naseeb.salestracker.data.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
