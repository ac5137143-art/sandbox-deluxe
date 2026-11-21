# R8 rules for Sandbox Deluxe.
#
# The engine and the UI are plain Kotlin; nothing is loaded reflectively, so no keep rules are
# required for the app's own code. These rules only protect what the platform reaches by name and
# trim the usual noise from the AndroidX/Kotlin metadata.

# Keep source/line information to make Play Console crash reports readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Views are inflated from XML by name.
-keep public class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Do not warn about optional dependencies that are absent at runtime.
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**
