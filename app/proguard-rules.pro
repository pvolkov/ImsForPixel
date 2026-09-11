# Instrumentation is started by name from `am instrument` and `app_process`.
-keep class com.pvolkov.imsforpixel.BrokerInstrumentation { *; }
-keep class com.pvolkov.imsforpixel.ImsQueryTool {
    public static void main(java.lang.String[]);
}
-keep class com.pvolkov.imsforpixel.ImsForPixelApp { *; }

# Telephony binders accessed by reflection from the shell identity.
-keep interface com.android.internal.telephony.** { *; }
-keep class com.android.internal.telephony.** { *; }
-keep class com.android.internal.telephony.**$* { *; }
-keep class android.os.ServiceManager { *; }

-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

-keep class com.flyfishxu.kadb.** { *; }
-dontwarn com.flyfishxu.kadb.**
