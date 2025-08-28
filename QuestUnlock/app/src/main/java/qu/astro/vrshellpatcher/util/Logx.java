package qu.astro.vrshellpatcher.util;
import qu.astro.vrshellpatcher.util.Constants;

import android.util.Log;

public final class Logx {
    private Logx() {}

    private static final boolean HAS_XPOSED;
    static {
        boolean present;
        try { Class.forName("de.robv.android.xposed.XposedBridge"); present = true; }
        catch (Throwable t) { present = false; }
        HAS_XPOSED = present;
    }

    private static void xlog(String msg) {
        if (!HAS_XPOSED) return;
        try {
            Class<?> xb = Class.forName("de.robv.android.xposed.XposedBridge");
            xb.getMethod("log", String.class).invoke(null, "[" + Constants.TAG + "] " + msg);
        } catch (Throwable ignore) {}
    }
    private static void xlog(Throwable t) {
        if (!HAS_XPOSED) return;
        try {
            Class<?> xb = Class.forName("de.robv.android.xposed.XposedBridge");
            xb.getMethod("log", Throwable.class).invoke(null, t);
        } catch (Throwable ignore) {}
    }

    public static void log(String msg)   { Log.i(Constants.TAG, msg); xlog(msg); }
    public static void debug(String msg) { Log.d(Constants.TAG, msg); xlog("[DEBUG] " + msg); }
    public static void warn(String msg)  { Log.w(Constants.TAG, msg); xlog("[WARN] " + msg); }
    public static void error(String msg) { Log.e(Constants.TAG, msg); xlog("[ERROR] " + msg); }
    public static void exc(Throwable t) { Log.e(Constants.TAG, "Exception", t); xlog(t); }
    public static void exc(String msg, Throwable t) { Log.e(Constants.TAG, msg, t); xlog(msg + " :: " + t); xlog(t); }
}
