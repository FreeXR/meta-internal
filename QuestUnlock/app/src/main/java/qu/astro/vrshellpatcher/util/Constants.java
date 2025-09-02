package qu.astro.vrshellpatcher.util;

public final class Constants {
    private Constants() {}

    // Keep log tag consistent with Logx.TAG
    public static final String TAG = "QVSP";

    // Broadcast to force-close DogfoodMainActivity before (re)hosting the panel
    public static final String ACTION_KILL_DOGFOOD = "qu.astro.vrshellpatcher.KILL_DOGFOOD";

    public static final String ACTION_RESTART_VR_SHELL = "qu.astro.vrshellpatcher.RESTART_VR_SHELL";
}
