package qu.astro.vrshellpatcher.generic;
import qu.astro.vrshellpatcher.util.Logx;

import android.content.Context;

import java.lang.reflect.Field;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class DeviceConfigHooks {
    private DeviceConfigHooks(){}

    public static void install(final XC_LoadPackage.LoadPackageParam lpp) {
        forceIsDebugEnvironment(lpp);
        forceEmployeeEverywhere(lpp);
    }

    private static void forceIsDebugEnvironment(final XC_LoadPackage.LoadPackageParam lpp) {
        final String CLS = "com.oculus.deviceconfigclient.DeviceConfigClient";
        try {
            XposedHelpers.findAndHookMethod(CLS, lpp.classLoader, "isDebugEnvironment",
                    XC_MethodReplacement.returnConstant(true));
            Class<?> dcc = XposedHelpers.findClass(CLS, lpp.classLoader);
            XposedBridge.hookAllConstructors(dcc, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    setBool(p.thisObject, "mIsDebugEnvironment", true);
                    setBool(p.thisObject, "isDebugEnvironment", true);
                    setBool(p.thisObject, "mIsEmployee", true);
                    setBool(p.thisObject, "isEmployee", true);
                }
            });
        } catch (Throwable t) { Logx.exc(t); }
    }

    private static void forceEmployeeEverywhere(final XC_LoadPackage.LoadPackageParam lpp) {
        try {
            XposedHelpers.findAndHookMethod("com.oculus.deviceconfigclient.DeviceConfigClient",
                    lpp.classLoader, "isEmployee",
                    XC_MethodReplacement.returnConstant(true));
        } catch (Throwable ignored) {}

        hookEmployeeishBoolean(lpp, "com.oculus.deviceconfigclient.ParamFetcher", "getBoolean");

        try {
            XposedHelpers.findAndHookMethod("com.oculus.deviceconfigclient.ParamFetcher",
                    lpp.classLoader, "getBooleanDefault$rvp0$0",
                    Context.class, String.class, boolean.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            String key = String.valueOf(p.args[1]);
                            if (isEmployeeish(key)) p.setResult(true);
                        }
                    });
        } catch (Throwable ignored) {}

        String[] mc = {
                "com.facebook.mobileconfig.MobileConfigManagerHolderImpl",
                "com.facebook.mobileconfig.factory.MobileConfig",
                "com.facebook.mobileconfig.factory.MobileConfigManager"
        };
        for (String cls : mc) hookEmployeeishBoolean(lpp, cls, "getBoolean");
    }

    private static void hookEmployeeishBoolean(final XC_LoadPackage.LoadPackageParam lpp, final String cls, final String method) {
        try {
            XposedHelpers.findAndHookMethod(cls, lpp.classLoader, method,
                    String.class, boolean.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            String key = String.valueOf(p.args[0]);
                            if (isEmployeeish(key)) p.setResult(true);
                        }
                    });
        } catch (Throwable ignored) {}
    }

    private static boolean isEmployeeish(String key) {
        if (key == null) return false;
        String k = key.toLowerCase();
        return k.contains("is_employee") || k.contains("employee") || k.contains("trusted_user")
                || k.contains("is_trusted") || k.contains("internal_user")
                || k.contains("allow_internal") || k.contains("debug_user");
    }

    private static void setBool(Object target, String field, boolean v) {
        try {
            Field f = XposedHelpers.findField(target.getClass(), field);
            f.setAccessible(true);
            f.setBoolean(target, v);
        } catch (Throwable ignored) {}
    }
}
