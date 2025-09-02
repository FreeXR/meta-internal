package qu.astro.vrshellpatcher.wm;

import android.app.Activity;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import qu.astro.vrshellpatcher.util.Logx;
import qu.astro.vrshellpatcher.util.State;

public final class WindowManagerAddViewHook {
    private WindowManagerAddViewHook() {}

    private static final ThreadLocal<Boolean> sRetry = new ThreadLocal<>();

    public static void install() {
        try {
            Class<?> wmg = XposedHelpers.findClass("android.view.WindowManagerGlobal", null);
            XposedBridge.hookAllMethods(wmg, "addView", new XC_MethodHook() {

                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!State.inDogfoodStandalone) return;

                    View v = findViewArg(p.args);
                    WindowManager.LayoutParams lp = findLpArg(p.args);
                    if (v == null || lp == null) return;
                    if (!shouldTouch(v, lp)) return;

                    // Always refresh token to a *current* valid one from focused Dogfood activity
                    IBinder fresh = getFreshToken();
                    if (fresh != null) {
                        lp.token = fresh;
                        Logx.debug("[IE] WM.addView → set fresh decor token");
                    }

                    // Sanitize type
                    if (lp.type == 2030) {
                        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL; // 1000
                        Logx.debug("[IE] WM.addView → downgraded 2030 → 1000");
                    }
                    if (lp.type == 0) {
                        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                        Logx.debug("[IE] WM.addView → fixed zero type → 1000");
                    }
                }

                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!State.inDogfoodStandalone) return;
                    Throwable t = p.getThrowable();
                    if (t == null) return;
                    if (!(t instanceof WindowManager.BadTokenException)) return;
                    if (Boolean.TRUE.equals(sRetry.get())) return;

                    View v = findViewArg(p.args);
                    WindowManager.LayoutParams lp = findLpArg(p.args);
                    if (v == null || lp == null) return;

                    try {
                        sRetry.set(Boolean.TRUE);

                        // Re-evaluate with freshest possible token and safe type
                        if (!isActivityAlive(State.lastActivity) || !isDecorUsable(State.lastDecor)) {
                            Logx.warn("[IE] WM.addView retry aborted: activity/decor not usable");
                            return;
                        }

                        IBinder fresh = getFreshToken();
                        if (fresh != null) lp.token = fresh;
                        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;

                        // Clear and retry original call
                        p.setThrowable(null);
                        XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                        Logx.debug("[IE] WM.addView: retried with fresh token/type after BadToken");
                    } catch (Throwable retryErr) {
                        Logx.exc("[IE] WM.addView retry failed", retryErr);
                    } finally {
                        sRetry.set(null);
                    }
                }
            });
            Logx.debug("[IE] WM addView universal token injector installed");
        } catch (Throwable t) {
            Logx.exc("[IE] installWMAddViewUniversal failed", t);
        }
    }

    // --- helpers ---
    private static View findViewArg(Object[] args) {
        if (args == null) return null;
        for (Object o : args) if (o instanceof View) return (View) o;
        return null;
    }

    private static WindowManager.LayoutParams findLpArg(Object[] args) {
        if (args == null) return null;
        for (Object o : args)
            if (o instanceof ViewGroup.LayoutParams && o instanceof WindowManager.LayoutParams)
                return (WindowManager.LayoutParams) o;
        return null;
    }

    private static boolean shouldTouch(View v, WindowManager.LayoutParams lp) {
        if (lp.type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION) return false;
        boolean looksPopup = v.getClass().getName().contains("PopupDecorView");
        boolean suspicious = lp.type == 0 || lp.type >= 2000 || lp.type == WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
        return looksPopup || suspicious;
    }

    private static boolean isActivityAlive(Activity a) {
        if (a == null) return false;
        if (a.isFinishing()) return false;
        try { if (a.isDestroyed()) return false; } catch (Throwable ignored) {}
        return true;
    }

    private static boolean isDecorUsable(View decor) {
        return decor != null && decor.isAttachedToWindow() && decor.getWindowToken() != null;
    }

    // ✅ Fresh, valid application window token from the currently focused Dogfood activity
    private static IBinder getFreshToken() {
        try {
            Activity a = State.lastActivity;
            if (!isActivityAlive(a)) return null;
            View decor = (a.getWindow() != null) ? a.getWindow().getDecorView() : null;
            if (!isDecorUsable(decor)) return null;

            IBinder appTok = decor.getApplicationWindowToken();
            if (appTok != null) return appTok;

            IBinder winTok = decor.getWindowToken();
            return winTok;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
