package qu.astro.vrshellpatcher;

import java.util.*;
import android.app.*;
import android.content.*;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import qu.astro.vrshellpatcher.misc.FloatParseGuard;
import qu.astro.vrshellpatcher.generic.BuildPropsHook;
import qu.astro.vrshellpatcher.generic.DeviceConfigHooks;
import qu.astro.vrshellpatcher.app.DebugReceiverMerge;
import qu.astro.vrshellpatcher.app.AppAttachBroadcast;
import qu.astro.vrshellpatcher.targets.vrshell.StandalonePanelHost;
import qu.astro.vrshellpatcher.wm.PopupWindowFixes;
import qu.astro.vrshellpatcher.wm.WindowManagerAddViewHook;
import qu.astro.vrshellpatcher.util.Logx;

public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            "com.oculus.vrshell", "com.oculus.systemux", "com.oculus.shellenv"
    ));

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        // Install early so it applies to boot classes (Float/FloatingDecimal)
        FloatParseGuard.install();
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpp) throws Throwable {
        if (!TARGET_PACKAGES.contains(lpp.packageName)) return;

        // generic env + flags
        BuildPropsHook.install(lpp);
        DeviceConfigHooks.install(lpp);

        // defensive receiver + one-shot DC override broadcast
        DebugReceiverMerge.install(lpp);
        AppAttachBroadcast.install(lpp);

        // window/token fixes (scoped by State.inDogfoodStandalone)
        PopupWindowFixes.install();
        WindowManagerAddViewHook.install();

        // Dogfood standalone host for ShellDebugPanelApp
        StandalonePanelHost.install(lpp);

        Logx.log("All hooks installed");
    }
}
