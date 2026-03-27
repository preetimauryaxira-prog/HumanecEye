
# Keep the SyncScheduler class
-keep class com.xira.humanec_eye_app.ui.camera.autoSync.SyncScheduler { *; }

-keepclassmembers class com.xira.humanec_eye_app.ui.camera.autoSync.SyncScheduler {
    public static void scheduleSync(android.content.Context);
}

-keepattributes Signature
-keepclassmembers class com.yourcompany.models.** {
  *;
  }