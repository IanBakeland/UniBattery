# WorkManager (pulled in by Glance) creates these classes by reflection. R8 full mode strips them or their
# constructors, which crashes the app on launch (WorkDatabase) or leaves every widget stuck on "loading"
# (InputMerger / Glance's session worker never runs).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
