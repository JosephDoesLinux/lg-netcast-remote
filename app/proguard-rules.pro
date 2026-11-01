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

# WorkManager 2.7 (from Glance) predates R8 full mode, so its own rules miss the classes it creates by reflection:
# Room's generated database, the workers Glance schedules for widget updates, and the input merger.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.InputMerger {
    <init>();
}
