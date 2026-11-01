# Release shrinking for Clicker for NetCast.

# Enum names are stored: custom layouts and macro steps save KeyCode names, and settings save Screen and Face names.
-keepclassmembers enum dev.netcastremote.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Saved state is kotlinx.serialization JSON; keep the generated serializers for the app's classes.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class dev.netcastremote.**$$serializer { *; }
-keepclassmembers class dev.netcastremote.** {
    *** Companion;
}
-keepclasseswithmembers class dev.netcastremote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Widget buttons name their ActionCallback class in the PendingIntent; Glance creates it by reflection.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
