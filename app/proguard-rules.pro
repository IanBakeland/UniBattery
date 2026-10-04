# WorkManager (pulled in by Glance) creates its Room database by reflection; R8 would strip it and the
# app crashes on launch with "Failed to create an instance of androidx.work.impl.WorkDatabase".
-keep class * extends androidx.room.RoomDatabase { <init>(); }
