package qu.astro.vrshellpatcher.app;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import qu.astro.vrshellpatcher.util.Logx;
import qu.astro.vrshellpatcher.util.State;

public final class AppAttachBroadcast {
    private AppAttachBroadcast(){}

    private static final String ACTION_DC_OVERRIDE = "oculus.intent.action.DC_OVERRIDE";
    private static final String EXTRA_NAME = "config_param_value";
    private static final String[] OVERRIDES = new String[] {
        "oculus_systemshell:oculus_is_trusted_user:true"
    };

    public static void install(final XC_LoadPackage.LoadPackageParam lpp) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        final Context ctx = (Context) p.args[0];
                        if (ctx == null) return;
                        if (!State.broadcastOnce.compareAndSet(false, true)) return;

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                sendDcOverride(ctx);
                                Logx.debug("DC_OVERRIDE broadcast sent");
                            } catch (Throwable t) {
                                Logx.exc("DC_OVERRIDE broadcast failed", t);
                            }
                        }, 2000);
                    }
                });
        } catch (Throwable t) {
            Logx.exc("Hook Application.attach failed", t);
        }
    }

    private static void sendDcOverride(Context ctx) throws Exception {
        Intent i = new Intent(ACTION_DC_OVERRIDE);
        i.putExtra(EXTRA_NAME, OVERRIDES);

        boolean sent = false;
        try {
            Class<?> uh = Class.forName("android.os.UserHandle");
            Field current = uh.getField("CURRENT");
            Object USER_CURRENT = current.get(null);
            Method m = Context.class.getMethod("sendBroadcastAsUser", Intent.class, uh);
            m.invoke(ctx, i, USER_CURRENT);
            sent = true;
        } catch (Throwable ignored) {
            // fall through to plain sendBroadcast
        }
        if (!sent) {
            ctx.sendBroadcast(i);
        }
    }
}
