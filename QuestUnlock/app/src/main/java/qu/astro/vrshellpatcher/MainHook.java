// app/src/main/java/qu/astro/vrshellpatcher/MainHook.java
package qu.astro.vrshellpatcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.app.Application;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import qu.astro.vrshellpatcher.misc.FloatParseGuard;
import qu.astro.vrshellpatcher.generic.BuildPropsHook;
import qu.astro.vrshellpatcher.generic.DeviceConfigHooks;
import qu.astro.vrshellpatcher.app.DebugReceiverMerge;
import qu.astro.vrshellpatcher.app.AppAttachBroadcast;
import qu.astro.vrshellpatcher.targets.vrshell.StandalonePanelHost;
import qu.astro.vrshellpatcher.wm.DropdownRewriter;
import qu.astro.vrshellpatcher.wm.PopupWindowHook;
import qu.astro.vrshellpatcher.wm.WindowManagerAddViewHook;
import qu.astro.vrshellpatcher.wm.InputMethodManagerFixes;
import qu.astro.vrshellpatcher.util.Logx;

public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            "com.oculus.vrshell", "com.oculus.systemux", "com.oculus.shellenv"
    ));

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        // Early hook to guard Float parsing (used by some occluded code-paths)
        FloatParseGuard.install();
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpp) throws Throwable {
        if (!TARGET_PACKAGES.contains(lpp.packageName)) return;

        // Environment flags
        BuildPropsHook.install(lpp);
        DeviceConfigHooks.install(lpp);

        // Receivers / one-shot override broadcast
        DebugReceiverMerge.install(lpp);
        AppAttachBroadcast.install(lpp);

        // --- ONLY UI/windowing helpers we actually need ---
        // 1) Rewrite dropdowns to render in-decor on display 0 (no VR popup surfaces)
        DropdownRewriter.install(lpp.classLoader);
        // 2) Fix stray popup/window tokens in VR shell
        WindowManagerAddViewHook.install(lpp.classLoader);
        PopupWindowHook.install(lpp.classLoader);
        // 3) ACTUAL FIX for the b/117267690 spam: rebind IME to the view’s root display
        InputMethodManagerFixes.install(lpp.classLoader);

        // Host ShellDebugPanelApp cleanly and fix the visible label/title
        StandalonePanelHost.install(lpp);

        Logx.log("All hooks installed");
    }
}
