# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate
# Explicit shell-only entry point, loaded by app_process from the APK payload.
-keep class com.sadique.launcher.ShellVolumeBridge { *; }
-keep class com.sadique.launcher.ShellVolumeBridge$* { *; }

# Public Conscrypt APIs used by libadb's supported TLS pairing path; no hidden-API bypass.
-keep class org.conscrypt.OpenSSLProvider { public <init>(...); }
-keep class org.conscrypt.Conscrypt { public static *** exportKeyingMaterial(...); }
