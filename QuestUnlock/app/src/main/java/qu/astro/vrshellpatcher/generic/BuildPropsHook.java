package qu.astro.vrshellpatcher.generic;
import qu.astro.vrshellpatcher.util.Logx;

import android.os.Build;

import java.lang.reflect.Field;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class BuildPropsHook {
    private BuildPropsHook(){}

    public static void install(final XC_LoadPackage.LoadPackageParam lpp) {
        // Build.TYPE -> userdebug
        try {
            Field f = XposedHelpers.findField(Build.class, "TYPE");
            f.setAccessible(true);
            Object old = f.get(null);
            f.set(null, "userdebug");
            Logx.debug("[IE] Build.TYPE: " + old + " -> userdebug");
        } catch (Throwable t) { Logx.exc(t); }

        // SystemProperties get(ro.build.type) -> userdebug
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", lpp.classLoader);
            XposedHelpers.findAndHookMethod(sp, "get", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if ("ro.build.type".equals(p.args[0])) p.setResult("userdebug");
                }
            });
            XposedHelpers.findAndHookMethod(sp, "get", String.class, String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if ("ro.build.type".equals(p.args[0])) p.setResult("userdebug");
                }
            });
        } catch (Throwable t) { Logx.exc(t); }
    }
}
