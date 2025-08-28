package qu.astro.vrshellpatcher.wm;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.IBinder;
import android.os.Looper;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.widget.PopupWindow;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import qu.astro.vrshellpatcher.util.Logx;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class PopupWindowHook {


    public static void install(ClassLoader cl) {
        hookConstructors(cl);
        hookShowMethods(cl);
        Logx.debug("[IE] Popup token fixes installed");
    }

    private static void hookConstructors(ClassLoader cl) {
        // Ensure mAttachedInDecor = false, because "in-decor" before decor attach = BadToken.
        XC_MethodHook ctorHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    PopupWindow pw = (PopupWindow) param.thisObject;
                    setBooleanFieldSafely(pw, "mAttachedInDecor", false);
                    // Clip to screen to avoid offscreen layout issues when we delay shows.
                    callMethodSafely(pw, "setIsClippedToScreen", new Class[]{boolean.class}, new Object[]{true});
                } catch (Throwable t) {
                    Logx.exc("[IE] ctor patch failed: ",  t);
                }
            }
        };
        // Hook the common PopupWindow constructors
        XposedHelpers.findAndHookConstructor(PopupWindow.class, Context.class, ctorHook);
        XposedHelpers.findAndHookConstructor(PopupWindow.class, Context.class, android.util.AttributeSet.class, int.class, int.class, ctorHook);
        XposedHelpers.findAndHookConstructor(PopupWindow.class, Context.class, android.util.AttributeSet.class, int.class, ctorHook);
        XposedHelpers.findAndHookConstructor(PopupWindow.class, Context.class, android.util.AttributeSet.class, ctorHook);
    }

    private static void hookShowMethods(ClassLoader cl) {
        // Guard + delay show until windowToken is valid; prevents BadToken during app transitions.
        XposedHelpers.findAndHookMethod(PopupWindow.class, "showAsDropDown",
                View.class, int.class, int.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        PopupWindow pw = (PopupWindow) param.thisObject;
                        View anchor = (View) param.args[0];
                        if (!ensureReadyOrRepost(pw, anchor, () -> {
                            // Reinvoke safely on UI once token is ready.
                            try {
                                pw.showAsDropDown(anchor, (Integer) param.args[1], (Integer) param.args[2], (Integer) param.args[3]);
                            } catch (Throwable ignored) {}
                        })) {
                            param.setResult(null); // we reposted; skip original call now
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(PopupWindow.class, "showAtLocation",
                View.class, int.class, int.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        PopupWindow pw = (PopupWindow) param.thisObject;
                        View parent = (View) param.args[0];
                        if (!ensureReadyOrRepost(pw, parent, () -> {
                            try {
                                pw.showAtLocation(parent, (Integer) param.args[1], (Integer) param.args[2], (Integer) param.args[3]);
                            } catch (Throwable ignored) {}
                        })) {
                            param.setResult(null);
                        }
                    }
                });
    }

    // Returns true if ready, false if it scheduled a repost.
    private static boolean ensureReadyOrRepost(PopupWindow pw, View anyViewInWindow, Runnable retry) {
        if (anyViewInWindow == null) return true; // let it crash like stock (shouldn't happen)
        final Handler h = new Handler(Looper.getMainLooper());

        Activity a = findActivity(anyViewInWindow.getContext());
        if (a == null || a.isFinishing() || a.isDestroyed()) return false;

        Window w = a.getWindow();
        if (w == null) return false;

        IBinder token = w.getDecorView() != null ? w.getDecorView().getWindowToken() : null;
        if (token != null) {
            // Good: the decor is attached. Also make sure PopupWindow won't fight us with decor attach.
            setBooleanFieldSafely(pw, "mAttachedInDecor", false);
            return true;
        }

        // Not attached yet → post a single-frame delay and try again with a valid token.
        h.postAtFrontOfQueue(retry);
        return false;
    }

    private static Activity findActivity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static void setBooleanFieldSafely(Object obj, String field, boolean value) {
        try { XposedHelpers.setBooleanField(obj, field, value); } catch (Throwable ignored) {}
    }

    private static void callMethodSafely(Object obj, String name, Class<?>[] sig, Object[] args) {
        try { XposedHelpers.callMethod(obj, name, args); } catch (Throwable ignored) {}
    }
}
