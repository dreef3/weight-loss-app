# Preserve Google Play services auth parcelables and intent results used by Drive authorization.
-keep class com.google.android.gms.auth.api.identity.** { *; }

# Preserve WorkManager workers referenced from manifest and reflection.
-keep class * extends androidx.work.ListenableWorker

# Keep Room generated implementations and entity field names used by JSON backup/restore.
-keep class androidx.room.** { *; }
-keep class com.dreef3.weightlossapp.data.local.** { *; }
