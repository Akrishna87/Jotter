# Compose and AndroidX ship their own consumer rules; the app needs none of its own.

# Native code calls back into Whisper.Progress by name.
-keep interface io.github.akrishna87.jotter.Whisper$Progress { *; }
-keepclassmembers class * implements io.github.akrishna87.jotter.Whisper$Progress { void onProgress(int); }
-keepclasseswithmembernames class io.github.akrishna87.jotter.Whisper { native <methods>; }
