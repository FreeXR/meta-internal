
package qu.astro.vrshellpatcher.util;
import qu.astro.vrshellpatcher.util.Logx;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.view.View;

public final class State {
    private State(){}

    public static final AtomicBoolean broadcastOnce = new AtomicBoolean(false);

    public static volatile Object debugPanelMainView;
    public static volatile Constructor<?> debugPanelCtor;
    public static volatile boolean libsLoadedOnce = false;

    public static volatile Activity lastActivity;
    public static volatile View lastDecor;

    public static final ThreadLocal<Boolean> popupRetry = new ThreadLocal<>();
    public static final ThreadLocal<Boolean> wmRetry = new ThreadLocal<>();

    // Scope flag: **only** true while Dogfood standalone host is active
    public static volatile boolean inDogfoodStandalone = false;
}
