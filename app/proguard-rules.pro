-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.**
-dontwarn okio.**

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

-keep class app.dastyar.agent.tools.DastyarAccessibilityService { *; }
-keep class app.dastyar.agent.tools.DastyarNotificationListener { *; }
-keep class app.dastyar.agent.AgentForegroundService { *; }
-keep class app.dastyar.agent.ProjectionForegroundService { *; }
-keep class app.dastyar.agent.RoutineAlarmReceiver { *; }
-keep class app.dastyar.agent.ToolPauseReceiver { *; }
-keep class app.dastyar.agent.BootReceiver { *; }
-keep class app.dastyar.agent.SmsTriggerReceiver { *; }
-keep class net.openid.appauth.** { *; }
-keep class rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.Shizuku {
    private static rikka.shizuku.ShizukuRemoteProcess newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
-keep class app.dastyar.agent.RoutineKeepAliveWorker { *; }
-keep class app.dastyar.agent.MainActivity { *; }
