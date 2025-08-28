package qu.astro.vrshellpatcher.util;
import qu.astro.vrshellpatcher.util.Logx;

import android.app.Activity;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import de.robv.android.xposed.XposedHelpers;

public final class UiHelpers {
    private UiHelpers(){}

    public static View findViewArg(Object[] args) {
        if (args == null) return null;
        for (Object o : args) if (o instanceof View) return (View) o;
        return null;
    }

    public static WindowManager.LayoutParams findLayoutParamsArg(Object[] args) {
        if (args == null) return null;
        for (Object o : args)
            if (o instanceof ViewGroup.LayoutParams && o instanceof WindowManager.LayoutParams)
                return (WindowManager.LayoutParams) o;
        return null;
    }

    public static boolean shouldTouchLayoutParams(View v, WindowManager.LayoutParams lp) {
        if (lp.type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION) return false;
        boolean looksLikePopup = v.getClass().getName().contains("PopupDecorView");
        boolean suspiciousType = lp.type == 0 || lp.type >= 2000
                || lp.type == WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
        return looksLikePopup || suspiciousType;
    }

    public static boolean isActivityAlive(Activity a) {
        if (a == null) return false;
        if (a.isFinishing()) return false;
        try { if (a.isDestroyed()) return false; } catch (Throwable ignored) {}
        return true;
    }

    public static boolean isDecorUsable(View decor) {
        return decor != null && decor.isAttachedToWindow();
    }

    public static IBinder getDecorToken() {
        try {
            View decor = State.lastDecor;
            if (!isDecorUsable(decor)) return null;
            Object tok = decor.getRootView().getApplicationWindowToken();
            if (tok != null) return (IBinder) XposedHelpers.callMethod(tok, "asBinder");
            Object tok2 = decor.getWindowToken();
            if (tok2 != null) return (IBinder) XposedHelpers.callMethod(tok2, "asBinder");
        } catch (Throwable ignored) {}
        return null;
    }
}
