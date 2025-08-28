package qu.astro.vrshellpatcher.wm;

import android.view.Display;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import qu.astro.vrshellpatcher.util.Logx;

public final class InputMethodManagerFixes {
    private InputMethodManagerFixes() {}

    public static void install(ClassLoader cl) {
        try {
            final Class<?> vriClass = XposedHelpers.findClass("android.view.ViewRootImpl", cl);

            // Hook *all* setView overloads (OEMs/Android versions differ)
            XposedBridge.hookAllMethods(vriClass, "setView", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    tryRebindImmToViewRootDisplay(param.thisObject);
                }
            });

            // Hook *all* performTraversals variants (some are hidden/renamed)
            XposedBridge.hookAllMethods(vriClass, "performTraversals", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    tryRebindImmToViewRootDisplay(param.thisObject);
                }
            });

            Logx.debug("[IE] IMM display-bound rebinding installed");
        } catch (Throwable t) {
            Logx.exc("[IE] IMM display-bound rebinding failed (install)", t);
        }
    }

    private static void tryRebindImmToViewRootDisplay(Object viewRootImpl) {
        try {
            final View rootView = (View) XposedHelpers.getObjectField(viewRootImpl, "mView");
            final Display display = (Display) XposedHelpers.getObjectField(viewRootImpl, "mDisplay");
            if (rootView == null || display == null) return;

            final int desiredDisplayId = display.getDisplayId();
            InputMethodManager imm = (InputMethodManager)
                    rootView.getContext().getSystemService(InputMethodManager.class);
            if (imm == null) return;

            Field fDisplayId = getImmDisplayIdField(imm);
            if (fDisplayId == null) return;

            fDisplayId.setAccessible(true);
            int currentId = fDisplayId.getInt(imm);
            if (currentId != desiredDisplayId) {
                fDisplayId.setInt(imm, desiredDisplayId);

                // Clear any fallback map cache if present
                try {
                    Field fFallback = XposedHelpers.findFieldIfExists(InputMethodManager.class, "mFallbackInputMethodManagerMap");
                    if (fFallback != null) {
                        fFallback.setAccessible(true);
                        Object map = fFallback.get(imm);
                        if (map instanceof java.util.Map) ((java.util.Map<?,?>) map).clear();
                    }
                } catch (Throwable ignored) {}

                Logx.debug("[IE] IMM re-bound to displayId=" + desiredDisplayId + " (was " + currentId + ")");
            }
        } catch (Throwable ignored) {
            // don’t spam per-frame
        }
    }

    private static Field getImmDisplayIdField(InputMethodManager imm) {
        Field f = XposedHelpers.findFieldIfExists(InputMethodManager.class, "mDisplayId");
        return f;
    }
}
