package qu.astro.vrshellpatcher.wm;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import qu.astro.vrshellpatcher.util.Logx;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class WindowManagerAddViewHook {

    private static final String TAG = "QVSP";

    public static void install(ClassLoader cl) {
        Class<?> wmImpl = XposedHelpers.findClass("android.view.WindowManagerImpl", cl);
        XposedHelpers.findAndHookMethod(wmImpl, "addView",
                View.class, android.view.ViewGroup.LayoutParams.class, new XC_MethodHook() {

                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        fixTokenIfPopup(param);
                    }

                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        // If the original call threw (BadToken), try once with a fresh windowToken from the focused Activity.
                        Throwable t = param.getThrowable();
                        if (t != null && t.toString().contains("BadTokenException")) {
                            param.setThrowable(null); // swallow once
                            View v = (View) param.args[0];
                            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.args[1];

                            if (!isPopupSubWindow(lp.type)) return;

                            Activity a = findActivity(v.getContext());
                            if (a == null || a.isFinishing() || a.isDestroyed()) return;

                            Window w = a.getWindow();
                            if (w == null || w.getDecorView() == null) return;

                            IBinder freshToken = w.getDecorView().getWindowToken();
                            if (freshToken == null) return;

                            lp.token = freshToken; // NOTE: sub-windows (1000/1002) need the parent *windowToken*
                            Logx.debug("[IE] WM.addView → set fresh decor windowToken (retry)");

                            try {
                                // retry on UI thread to avoid racing the transition
                                new Handler(Looper.getMainLooper()).postAtFrontOfQueue(() -> {
                                    try {
                                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                                    } catch (Throwable inner) {
                                        Logx.exc("[IE] WM.addView retry failed: ", inner);
                                    }
                                });
                            } catch (Throwable inv) {
                                Logx.exc("[IE] WM.addView retry schedule failed: ",  inv);
                            }
                        }
                    }
                });
    }

    private static void fixTokenIfPopup(XC_MethodHook.MethodHookParam param) {
        View v = (View) param.args[0];
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.args[1];

        if (!isPopupSubWindow(lp.type)) return;

        Activity a = findActivity(v.getContext());
        if (a == null || a.isFinishing() || a.isDestroyed()) return;

        Window w = a.getWindow();
        if (w == null || w.getDecorView() == null) return;

        IBinder winToken = w.getDecorView().getWindowToken();
        if (winToken != null) {
            lp.token = winToken; // IMPORTANT: use windowToken for 1000/1002
            // Don't change type; Android uses it to validate sub-window parenting.
            Logx.debug("[IE] WM.addView → set fresh decor windowToken");
        }
    }

    private static boolean isPopupSubWindow(int type) {
        // TYPE_APPLICATION_PANEL (1000), TYPE_APPLICATION_SUB_PANEL (1002), TYPE_APPLICATION_ATTACHED_DIALOG (1003)
        return type >= 1000 && type <= 1003;
    }

    private static Activity findActivity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }
}
