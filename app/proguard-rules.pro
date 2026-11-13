# R8 rules for the "fast" and release builds. Libraries (Room, WorkManager, Compose) ship their own.

# Google code scanner (Scan & Pay, M7): ML Kit finds its parts by reflection at runtime, so the
# shrinker must keep them, or the scanner crashes the app as it opens.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.firebase.components.** { *; }
