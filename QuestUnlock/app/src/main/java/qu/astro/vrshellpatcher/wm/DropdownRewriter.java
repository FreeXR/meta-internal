package qu.astro.vrshellpatcher.wm;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import qu.astro.vrshellpatcher.util.Logx;

/**
 * Forces dropdowns (Spinner adapters / AutoCompleteTextView) that use
 * ListPopupWindow to attach to the hosting window token and behave as
 * in-decor HUD (no stray VR displays).
 *
 * We intentionally DO NOT hook Spinner$DropdownPopup due to OEM diffs.
 */
public final class DropdownRewriter {

    private DropdownRewriter() {}

    public static void install(ClassLoader cl) {
        try {
            final Class<?> lpw = XposedHelpers.findClass("android.widget.ListPopupWindow", cl);

            // Hook show() to fix the popup token + basic flags before display
            XposedHelpers.findAndHookMethod(lpw, "show", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    ListPopupWindow self = (ListPopupWindow) param.thisObject;

                    View anchor = (View) XposedHelpers.getObjectField(self, "mAnchorView");
                    if (anchor == null) return;

                    PopupWindow popup = (PopupWindow) XposedHelpers.getObjectField(self, "mPopup");
                    if (popup == null) return;

                    // Ensure we use the main app window token (application panel)
                    Object token = tryGetAppWindowToken(anchor);
                    if (token != null) {
                        popup.setWindowLayoutType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL);
                        popup.setTouchModal(true);
                        XposedHelpers.callMethod(popup, "setWindowToken", token);
                    }

                    // Make sure it's positioned inside the decor (safe offsets)
                    tryFixOffsets(self, anchor);

                    // Prefer overlap so it reads like an inline dropdown in HUD
                    trySetOverlapAnchor(self, true);
                }
            });

            Logx.debug("[IE] Dropdown rewriter installed");
        } catch (Throwable t) {
            Logx.exc("[IE] Dropdown rewriter failed", t);
        }
    }

    private static Object tryGetAppWindowToken(View anchor) {
        try {
            // Walk up to find a Window (via decor)
            View root = anchor.getRootView();
            if (root == null) return null;

            // Try getViewRootImpl -> mWindowAttributes.token
            Object vri = XposedHelpers.callMethod(root, "getViewRootImpl");
            if (vri != null) {
                Object attrs = XposedHelpers.getObjectField(vri, "mWindowAttributes");
                if (attrs != null) {
                    return XposedHelpers.getObjectField(attrs, "token");
                }
            }

            // Fallback: anchor.getApplicationWindowToken()
            return anchor.getApplicationWindowToken();
        } catch (Throwable ignored) {
            return anchor.getApplicationWindowToken();
        }
    }

    private static void tryFixOffsets(ListPopupWindow self, View anchor) {
        try {
            // Keep dropdown inside the visible decor frame
            Rect vis = new Rect();
            anchor.getWindowVisibleDisplayFrame(vis);

            int xOff = XposedHelpers.getIntField(self, "mDropDownHorizontalOffset");
            int yOff = XposedHelpers.getIntField(self, "mDropDownVerticalOffset");

            int[] loc = new int[2];
            anchor.getLocationInWindow(loc);

            // If the anchor would push content out of the window, clamp it in
            int anchorLeft = loc[0] + xOff;
            if (anchorLeft < vis.left) {
                XposedHelpers.setIntField(self, "mDropDownHorizontalOffset", xOff + (vis.left - anchorLeft));
            }
            // similar vertical clamp
            int anchorTop = loc[1] + anchor.getHeight() + yOff;
            if (anchorTop > vis.bottom) {
                int delta = anchorTop - vis.bottom;
                XposedHelpers.setIntField(self, "mDropDownVerticalOffset", yOff - delta);
            }
        } catch (Throwable ignored) { }
    }

    private static void trySetOverlapAnchor(ListPopupWindow self, boolean overlap) {
        try {
            XposedHelpers.callMethod(self, "setOverlapAnchor", overlap);
        } catch (Throwable ignored) { }
    }
}
