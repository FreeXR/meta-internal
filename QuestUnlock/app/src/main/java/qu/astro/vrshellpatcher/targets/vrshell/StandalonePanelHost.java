package qu.astro.vrshellpatcher.targets.vrshell;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import qu.astro.vrshellpatcher.util.Logx;
import qu.astro.vrshellpatcher.util.State;

/**
 * Hosts ShellDebugPanelApp inside DogfoodMainActivity *without* relying on
 * Dogfood lifecycle peculiarities. Also sets a clean title.
 */
public final class StandalonePanelHost {
    private StandalonePanelHost(){}

    private static final String DebugPanelApp   = "com.oculus.panelapp.debug.ShellDebugPanelApp";
    private static final String DogfoodActivity = "com.oculus.panelapp.dogfood.DogfoodMainActivity";
    public  static final String ACTION_KILL_DOGFOOD = "qu.astro.vrshellpatcher.KILL_DOGFOOD";

    public static void install(final XC_LoadPackage.LoadPackageParam lpp) {
        try {
            Class<?> debugCls = XposedHelpers.findClass(DebugPanelApp, lpp.classLoader);
            final Field fMainView = XposedHelpers.findField(debugCls, "mMainView");
            fMainView.setAccessible(true);

            for (Constructor<?> c : debugCls.getConstructors()) {
                Class<?>[] ps = c.getParameterTypes();
                boolean hasSurface = false;
                for (Class<?> p : ps) if (Surface.class.isAssignableFrom(p)) { hasSurface = true; break; }
                if (hasSurface && ps.length >= 4) { State.debugPanelCtor = c; break; }
            }
            if (State.debugPanelCtor == null && debugCls.getConstructors().length > 0) {
                State.debugPanelCtor = debugCls.getConstructors()[0];
            }

            // Dogfood onCreate – host the debug panel view and set sane title
            XposedHelpers.findAndHookMethod(
                    DogfoodActivity, lpp.classLoader, "onCreate",
                    Bundle.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            final Activity activity = (Activity) param.thisObject;
                            State.lastActivity = activity;
                            State.lastDecor = (activity.getWindow()!=null) ? activity.getWindow().getDecorView() : null;

                            registerKillReceiver(activity);

                            boolean wantStandalone = wantsStandaloneDebug(activity.getIntent());
                            State.inDogfoodStandalone = wantStandalone;
                            if (!wantStandalone) return;

                            loadPanelLibsOnce(activity.getClassLoader());

                            Object dbg = ensureDebugPanelConstructed(activity, param.args[0]);
                            if (dbg == null) return;

                            try {
                                Object mv = fMainView.get(dbg);
                                State.debugPanelMainView = mv;
                                if (mv instanceof View) {
                                    View v = (View) mv;
                                    if (v.getParent() instanceof ViewGroup) {
                                        ((ViewGroup) v.getParent()).removeView(v);
                                    }
                                    v.setLayoutParams(new ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT));
                                    activity.setContentView(v);
                                    activity.setTitle("VR Shell Debug Panel"); // <- fixed title
                                    State.lastDecor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
                                }
                            } catch (Throwable t) { Logx.exc("Inject mainView failed", t); }
                        }
                    });

            // Base Activity hooks → keep freshest decor/token
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Activity a = (Activity) param.thisObject;
                    if (a.getClass().getName().equals(DogfoodActivity)) {
                        State.lastActivity = a;
                        State.lastDecor = (a.getWindow()!=null) ? a.getWindow().getDecorView() : null;
                    }
                }
            });

            // Dogfood onDestroy cleanup; avoid double finish side effects
            try {
                XposedHelpers.findAndHookMethod(
                        DogfoodActivity, lpp.classLoader, "onDestroy",
                        new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam p) {
                                State.debugPanelMainView = null;
                                State.lastDecor = null;
                                State.lastActivity = null;
                                State.inDogfoodStandalone = false;
                            }
                        });
            } catch (Throwable ignored) {}

            Logx.debug("[IE] StandalonePanelHost installed");
        } catch (Throwable t) {
            Logx.exc("StandalonePanelHost.install failed", t);
        }
    }

    private static void registerKillReceiver(final Activity dogfood) {
        try {
            IntentFilter f = new IntentFilter(ACTION_KILL_DOGFOOD);
            dogfood.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    try {
                        if (!dogfood.isFinishing() && !dogfood.isDestroyed()) {
                            dogfood.finish();
                        }
                    } catch (Throwable ignored) {}
                }
            }, f);
        } catch (Throwable t) {
            Logx.exc("[IE] registerKillReceiver", t);
        }
    }

    private static boolean wantsStandaloneDebug(Intent in) {
        if (in == null) return false;
        if (in.hasExtra("qu.astro.debug_container")) {
            return "1".equals(String.valueOf(in.getStringExtra("qu.astro.debug_container")))
                    || in.getBooleanExtra("qu.astro.debug_container", false);
        }
        String qp = in.getStringExtra("queryParametersUri");
        if (qp != null) {
            Map<String,String> q = parseQueryString(qp);
            if ("debug".equalsIgnoreCase(q.get("host"))) return true;
            if ("1".equals(q.get("debugpanel"))) return true;
        }
        String uri = in.getStringExtra("uri");
        if (uri != null) {
            try {
                Uri u = Uri.parse(uri);
                String host = u.getQueryParameter("host");
                String dp = u.getQueryParameter("debugpanel");
                if ("debug".equalsIgnoreCase(host)) return true;
                if ("1".equals(dp)) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static Map<String,String> parseQueryString(String qp) {
        Map<String,String> out = new HashMap<>();
        if (qp == null) return out;
        for (String p : qp.split("&")) {
            int i = p.indexOf('=');
            if (i > 0) out.put(p.substring(0,i), (i+1<p.length()) ? p.substring(i+1) : "");
            else if (!p.isEmpty()) out.put(p, "");
        }
        return out;
    }

    private static Object ensureDebugPanelConstructed(Activity activity, Object bundleArg) {
        try {
            if (State.debugPanelCtor == null) return null;

            Bundle bundle = (bundleArg instanceof Bundle) ? (Bundle) bundleArg : new Bundle();

            // Tiny Surface to satisfy ctors expecting a Surface
            SurfaceView sv = new SurfaceView(activity);
            if (activity.getWindow()!=null) {
                View decor = activity.getWindow().getDecorView();
                if (decor instanceof ViewGroup) {
                    ((ViewGroup) decor).addView(sv, new ViewGroup.LayoutParams(1,1));
                }
            }
            Surface s = sv.getHolder().getSurface();
            if (s == null || !s.isValid()) {
                SurfaceTexture st = new SurfaceTexture(0);
                st.setDefaultBufferSize(64, 64);
                s = new Surface(st);
            }

            Map<String,Object> env = new HashMap<>();
            Class<?>[] ps = State.debugPanelCtor.getParameterTypes();
            Object[] args = new Object[ps.length];
            for (int i = 0; i < ps.length; i++) {
                Class<?> p = ps[i];
                args[i] =
                        Application.class.isAssignableFrom(p) ? activity.getApplication() :
                        Activity.class.isAssignableFrom(p) || Context.class.isAssignableFrom(p) ? activity :
                        Bundle.class.isAssignableFrom(p) ? bundle :
                        Surface.class.isAssignableFrom(p) ? s :
                        Map.class.isAssignableFrom(p) ? env : null;
            }

            Object dbg = State.debugPanelCtor.newInstance(args);

            if (activity.getWindow()!=null) {
                View decor = activity.getWindow().getDecorView();
                if (decor instanceof ViewGroup) {
                    ((ViewGroup) decor).removeView(sv);
                }
            }
            return dbg;
        } catch (Throwable t) {
            Logx.exc("ensureDebugPanelConstructed failed", t);
            return null;
        }
    }

    private static void loadPanelLibsOnce(ClassLoader cl) {
        if (State.libsLoadedOnce) return;
        State.libsLoadedOnce = true;

        String[] libs = {
                "panelappsdk","vrshelldebugpanel","shell","vrshell_notifications",
                "vrshellkeyboardv2","vrshellgauntlettestpanel","folly","gifimage",
                "imagepipeline","native-imagetranscoder","native-filters","profiloextapi",
                "flatbuffers","flatbuffersflatc","c++_shared"
        };
        boolean any = false;
        for (String lib : libs) any |= loadLibWithCL(cl, lib);
        if (!any) Logx.log("[IE] WARNING: no native panel libs loaded");
    }

    private static boolean loadLibWithCL(ClassLoader cl, String lib) {
        try {
            Method m = Runtime.class.getDeclaredMethod("loadLibrary0", ClassLoader.class, String.class);
            m.setAccessible(true);
            m.invoke(Runtime.getRuntime(), cl, lib);
            return true;
        } catch (Throwable t) {
            Logx.exc("loadLibWithCL("+lib+") failed", t);
            return false;
        }
    }
}
