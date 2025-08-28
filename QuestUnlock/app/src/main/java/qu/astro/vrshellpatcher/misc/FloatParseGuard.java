package qu.astro.vrshellpatcher.misc;

import qu.astro.vrshellpatcher.util.Logx;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class FloatParseGuard {
    private FloatParseGuard() {}

    public static void install() {
        // Guard java.lang.Float.parseFloat(String) — catch null before ART dives deeper
        try {
            XposedBridge.hookAllMethods(Float.class, "parseFloat", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args != null && p.args.length >= 1 && p.args[0] == null) {
                        p.setResult(0f);
                        Logx.debug("[IE] Float.parseFloat(null) -> 0f [guard]");
                    }
                }
            });
            Logx.debug("[IE] Hooked Float.parseFloat(String)");
        } catch (Throwable t) {
            Logx.exc("[IE] Hook Float.parseFloat failed", t);
        }

        // Guard ART/JDK path: jdk.internal.math.FloatingDecimal
        try {
            Class<?> fd = XposedHelpers.findClassIfExists("jdk.internal.math.FloatingDecimal", null);
            if (fd != null) {
                // 1) Guard FloatingDecimal.parseFloat(String)
                try {
                    XposedBridge.hookAllMethods(fd, "parseFloat", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (p.args != null && p.args.length >= 1 && p.args[0] == null) {
                                p.setResult(0f);
                                Logx.debug("[IE] FloatingDecimal.parseFloat(null) -> 0f [guard]");
                            }
                        }
                    });
                    Logx.debug("[IE] Hooked FloatingDecimal.parseFloat(String)");
                } catch (Throwable t) {
                    Logx.exc("[IE] Hook FloatingDecimal.parseFloat failed", t);
                }

                // 2) Guard the deeper path seen in logs: readJavaFormatString(String)
                //    Coerce null → "0" before it calls trim() and NPEs.
                try {
                    XposedBridge.hookAllMethods(fd, "readJavaFormatString", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (p.args != null && p.args.length >= 1 && p.args[0] == null) {
                                p.args[0] = "0";
                                Logx.debug("[IE] Coerced null → \"0\" in FloatingDecimal.readJavaFormatString");
                            }
                        }
                    });
                    Logx.debug("[IE] Hooked FloatingDecimal.readJavaFormatString(String)");
                } catch (Throwable t) {
                    Logx.exc("[IE] Hook FloatingDecimal.readJavaFormatString failed", t);
                }
            } else {
                Logx.warn("[IE] FloatingDecimal class not found; relying on Float.parseFloat hook");
            }
        } catch (Throwable t) {
            Logx.exc("[IE] FloatingDecimal discovery failed", t);
        }
    }
}
