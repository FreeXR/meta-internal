package qu.astro.vrshellpatcher.app;
import qu.astro.vrshellpatcher.util.Logx;

import android.content.*;
import java.lang.reflect.Method;
import java.util.*;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class DebugReceiverMerge {
    private DebugReceiverMerge(){}

    private static final String ACTION_DC_OVERRIDE = "oculus.intent.action.DC_OVERRIDE";
    private static final String EXTRA_NAME = "config_param_value";
    private static final String[] OVERRIDES = new String[] {
        "oculus_systemshell:oculus_is_trusted_user:true"
    };

    public static void install(final XC_LoadPackage.LoadPackageParam lpp) {
        final String RX = "com.oculus.deviceconfigclient.DeviceConfigClientDebugReceiver";
        try {
            Class<?> rxCls = XposedHelpers.findClassIfExists(RX, lpp.classLoader);
            if (rxCls == null) return;

            Method candidate = null;
            for (Method m : rxCls.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if ("onReceive".equals(m.getName()) && ps.length == 2
                        && Context.class.isAssignableFrom(ps[0])
                        && Intent.class.isAssignableFrom(ps[1])) { candidate = m; break; }
            }
            if (candidate == null) return;

            XposedBridge.hookMethod(candidate, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Intent i = (Intent) p.args[1];
                    if (i == null || !ACTION_DC_OVERRIDE.equals(i.getAction())) return;
                    String[] merged = merge(i.getStringArrayExtra(EXTRA_NAME), OVERRIDES);
                    i.putExtra(EXTRA_NAME, merged);
                    p.args[1] = i;
                }
            });
        } catch (Throwable t) { Logx.exc(t); }
    }

    private static String[] merge(String[] existing, String[] add) {
        if (existing == null || existing.length == 0) return add.clone();
        Set<String> s = new HashSet<>(Arrays.asList(existing));
        s.addAll(Arrays.asList(add));
        return s.toArray(new String[0]);
    }
}
