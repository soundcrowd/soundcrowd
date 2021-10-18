# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/google/home/mangini/tools/android-studio/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
-keep class com.tiefensuche.soundcrowd.plugins.** { *; }
-keep class com.tiefensuche.soundcrowd.extensions.UrlResolver { *; }
-keep class android.support.v4.media.** { *; }
-keep class androidx.preference.** { *; }
-keep class kotlin.** { *; }
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int wtf(...);
    public static int e(...);
    public static int w(...);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}