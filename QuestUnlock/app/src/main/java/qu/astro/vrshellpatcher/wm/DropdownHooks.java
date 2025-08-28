package qu.astro.vrshellpatcher.wm;

import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow;
import qu.astro.vrshellpatcher.util.Logx;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class DropdownHooks {
    private static final String TAG = "QVSP";

    public static void install(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(ListPopupWindow.class, "show", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    handleDropdownShow(param.thisObject, param);
                }
            });

            // Also hook Spinner's inner DropdownPopup (it subclasses ListPopupWindow)
            Class<?> dd = XposedHelpers.findClass("android.widget.Spinner$DropdownPopup", cl);
            XposedHelpers.findAndHookMethod(dd, "show", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    handleDropdownShow(param.thisObject, param);
                }
            });

            Logx.log("[IE] Dropdown hooks installed");
        } catch (Throwable t) {
            Logx.exc("[IE] Dropdown hook failed: ", t);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static void handleDropdownShow(Object listPopupWindow, XC_MethodHook.MethodHookParam param) {
        try {
            final View anchor = (View) XposedHelpers.getObjectField(listPopupWindow, "mAnchor");
            if (anchor == null) return; // let it behave as stock (rare)

            final PopupWindow pw = (PopupWindow) XposedHelpers.getObjectField(listPopupWindow, "mPopup");
            if (pw == null) return;

            // Ensure this behaves as a sub-window attached to the anchor's window
            try { XposedHelpers.callMethod(pw, "setAttachedInDecor", false); } catch (Throwable ignored) {}
            try { XposedHelpers.callMethod(pw, "setWindowLayoutType", WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL); } catch (Throwable ignored) {}

            if (isAnchorReady(anchor)) {
                IBinder token = anchor.getWindowToken();
                if (token != null) {
                    // Force the correct token **for sub-window popups**
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) XposedHelpers.getObjectField(pw, "mWindowLayoutParams");
                    if (lp != null) lp.token = token;
                }
                return; // allow original show() to proceed
            }

            // Not ready → reschedule a single-frame retry and swallow this call
            param.setResult(null);
            new Handler(Looper.getMainLooper()).postAtFrontOfQueue(() -> {
                try {
                    // Double-check readiness after a frame
                    if (isAnchorReady(anchor)) {
                        IBinder token = anchor.getWindowToken();
                        if (token != null) {
                            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) XposedHelpers.getObjectField(pw, "mWindowLayoutParams");
                            if (lp != null) lp.token = token;
                        }
                        XposedHelpers.callMethod(listPopupWindow, "show");
                    } else {
                        // One last tiny delay (rare race during transitions)
                        new Handler(Looper.getMainLooper()).post(() -> {
                            try { XposedHelpers.callMethod(listPopupWindow, "show"); } catch (Throwable ignored) {}
                        });
                    }
                } catch (Throwable t) {
                    Logx.exc("[IE] Dropdown deferred show failed: ", t);
                }
            });
        } catch (Throwable t) {
            Logx.exc("[IE] handleDropdownShow error: ", t);
        }
    }

    private static boolean isAnchorReady(View v) {
        return v.isAttachedToWindow()
                && v.getWindowToken() != null
                && v.getWidth() > 0
                && v.getHeight() > 0
                && v.getViewTreeObserver().isAlive();
    }
}
