# Strip android.util.Log calls from release builds (SEC-04).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
# SQLCipher JNI entry points.
-keep class net.zetetic.database.** { *; }

# PdfBox-Android: the JPEG 2000 codec is an optional dependency; reports never contain JPX images.
-dontwarn com.gemalto.jp2.**
# SecurityHandlerFactory instantiates handlers through reflection on their constructors.
-keepclassmembers class com.tom_roush.pdfbox.pdmodel.encryption.** { <init>(...); }

# MediaPipe: native graph code calls back into its Java classes; its full protobuf-java (javalite is excluded at build) reads fields reflectively.
-keep class com.google.mediapipe.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
# Graph profiling and templates are not shipped in the library and never used here.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto**
-dontwarn com.google.mediapipe.proto.GraphTemplateProto**
# Protobuf's message schemas are found by reflection on generated field names; shrinking them leaves MediaPipe's graph unable to open.
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn com.google.protobuf.**
# Flogger, which MediaPipe's Graph logs through, finds its caller by class name on the stack; renaming makes Graph fail to load.
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**
