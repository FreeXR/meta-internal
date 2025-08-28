package qu.astro.vrshellpatcher.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import de.robv.android.xposed.XposedHelpers;
import qu.astro.vrshellpatcher.util.Logx;

public final class MethodDump {
    private MethodDump() {}

    public static void dump(ClassLoader cl, String fqcn) {
        try {
            Class<?> c = XposedHelpers.findClass(fqcn, cl);
            Method[] ms = c.getDeclaredMethods();
            for (Method m : ms) {
                StringBuilder sb = new StringBuilder();
                sb.append(Modifier.toString(m.getModifiers()))
                  .append(' ')
                  .append(m.getReturnType().getSimpleName())
                  .append(' ')
                  .append(m.getName())
                  .append('(');
                Class<?>[] ps = m.getParameterTypes();
                for (int i=0;i<ps.length;i++) {
                    if (i>0) sb.append(", ");
                    sb.append(ps[i].getName());
                }
                sb.append(')');
                Logx.error("[DUMP] " + fqcn + " :: " + sb);
            }
        } catch (Throwable t) {
            Logx.exc("[DUMP] failed for " + fqcn, t);
        }
    }
}
