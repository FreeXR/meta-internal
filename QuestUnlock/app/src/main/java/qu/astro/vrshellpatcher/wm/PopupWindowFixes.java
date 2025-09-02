package qu.astro.vrshellpatcher.wm;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import qu.astro.vrshellpatcher.util.Logx;
import qu.astro.vrshellpatcher.util.State;

public final class PopupWindowFixes {
    private PopupWindowFixes() {}

    private static final ThreadLocal<Boolean> sRetry = new ThreadLocal<>();

    public static void install() {
        try {
            XposedBridge.hookAllConstructors(PopupWindow.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!State.inDogfoodStandalone) return;
                    try {
                        Method setAttachedInDecor = PopupWindow.class.getMethod("setAttachedInDecor", boolean.class);
                        setAttachedInDecor.invoke(param.thisObject, true);
                    } catch (Throwable ignored) {}
                    try {
                        Method setWindowLayoutType = PopupWindow.class.getMethod("setWindowLayoutType", int.class);
                        setWindowLayoutType.invoke(param.thisObject, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL); // 1000
                        Logx.debug("[IE] PopupWindow ctor → attachedInDecor=true, type=1000");
                    } catch (Throwable ignored) {}
                }
            });

            for (Method m : PopupWindow.class.getDeclaredMethods()) {
                String n = m.getName();
                if (!"showAsDropDown".equals(n) && !"showAtLocation".equals(n)) continue;

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!State.inDogfoodStandalone) return;

                        if ("showAsDropDown".equals(p.method.getName())) {
                            if (p.args != null && p.args.length > 0 && p.args[0] instanceof View) {
                                View anchor = (View) p.args[0];
                                if (anchor == null || !anchor.isAttachedToWindow()) {
                                    View decor = State.lastDecor;
                                    if (isDecorUsable(decor)) {
                                        p.args[0] = decor;
                                        Logx.debug("[IE] PopupWindow: detached anchor → decor (pre)");
                                    }
                                }
                            }
                        } else { // showAtLocation
                            if (p.args != null && p.args.length > 0 && p.args[0] instanceof View) {
                                View parent = (View) p.args[0];
                                if (parent == null || !parent.isAttachedToWindow()) {
                                    View decor = State.lastDecor;
                                    if (isDecorUsable(decor)) {
                                        p.args[0] = decor;
                                        Logx.debug("[IE] PopupWindow: detached parent → decor (pre)");
                                    }
                                }
                            }
                        }
                    }

                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!State.inDogfoodStandalone) return;
                        if (!p.hasThrowable()) return;
                        Throwable t = p.getThrowable();
                        if (!(t instanceof WindowManager.BadTokenException)) return;
                        if (Boolean.TRUE.equals(sRetry.get())) return;

                        try {
                            sRetry.set(Boolean.TRUE);

                            PopupWindow pw = (PopupWindow) p.thisObject;
                            View decor = State.lastDecor;

                            if (!isDecorUsable(decor)) {
                                Logx.warn("[IE] PopupWindow retry aborted: decor unusable");
                                return;
                            }

                            p.setThrowable(null);
                            try { pw.dismiss(); } catch (Throwable ignored) {}

                            Object[] args = p.args.clone();
                            if (args.length >= 1) args[0] = decor;
                            XposedBridge.invokeOriginalMethod(p.method, p.thisObject, args);
                            Logx.debug("[IE] PopupWindow: retried with decor (post)");
                        } catch (Throwable retryErr) {
                            Logx.exc("[IE] PopupWindow retry failed", retryErr);
                        } finally {
                            sRetry.set(null);
                        }
                    }
                });
            }

            Logx.debug("[IE] Popup token fixes installed");
        } catch (Throwable t) {
            Logx.exc("[IE] installScopedPopupTokenFixes failed", t);
        }
    }

    private static boolean isDecorUsable(View v) {
        return v != null && v.isAttachedToWindow() && v.getWindowToken() != null;
    }
}
