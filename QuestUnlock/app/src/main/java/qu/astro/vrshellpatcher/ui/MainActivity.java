package qu.astro.vrshellpatcher.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;

import qu.astro.vrshellpatcher.util.Constants;
import qu.astro.vrshellpatcher.util.Logx;

public class MainActivity extends Activity implements View.OnClickListener, DialogInterface.OnClickListener {

    private static final String VR_SHELL_PKG = "com.oculus.vrshell";
    private static final String DOGFOOD_ACTIVITY = "com.oculus.panelapp.dogfood.DogfoodMainActivity";

    private Button startBtn;
    private Button aboutBtn;
    private Button restartShellBtn;

    private LinearLayout createLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        startBtn = new Button(this);
        startBtn.setText("Open debug panel");
        startBtn.setOnClickListener(this);
        
        restartShellBtn = new Button(this);
        restartShellBtn.setText("Restart VR Shell");
        restartShellBtn.setOnClickListener(this);

        aboutBtn = new Button(this);
        aboutBtn.setText("About");
        aboutBtn.setOnClickListener(this);

        layout.addView(startBtn, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        layout.addView(restartShellBtn, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        layout.addView(aboutBtn, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return layout;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createLayout());
    }

    @Override
    public void onClick(View v) {
        if (v == aboutBtn) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle("About")
                    .setMessage("Xposed module for patching vrshell and enabling debug features\nCredits:\n• xAstroBoy\n• GeoKar")
                    .setNeutralButton("OK", this);
            builder.show();
            return;
        }

        if (v == restartShellBtn) {
            restartVrShellCompletely(/* thenOpenDebug */ true);
            return;
        }

        if (v == startBtn) {
            openDebugPanel();
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // no-op for About dialog OK
    }

    // === Implementation ===

    private void restartVrShellCompletely(boolean thenOpenDebug) {
        Toast.makeText(this, "Restarting VR Shell…", Toast.LENGTH_SHORT).show();
        Logx.debug("Restarting VR Shell (full stop + kill sweep)");

        new Thread(() -> {
            boolean ok = false;
            try {
                // Prefer a hard sweep via root shell
                ok = runSuJoined(new String[]{
                        // Best-effort: tell ActivityManager to force-stop
                        "cmd activity force-stop " + VR_SHELL_PKG + " || am force-stop " + VR_SHELL_PKG + " || true",
                        // Kill any surviving processes for the package (handles :process suffixes)
                        // Use toybox/busybox tools present on Q3 (toybox has killall/pidof)
                        "if command -v killall >/dev/null 2>&1; then " +
                                "killall -9 " + VR_SHELL_PKG + " 2>/dev/null || true; " +
                                "fi",
                        "if command -v pidof >/dev/null 2>&1; then " +
                                "for P in $(pidof " + VR_SHELL_PKG + " 2>/dev/null || true); do kill -9 \"$P\" 2>/dev/null || true; done; " +
                                "fi",
                        // Small delay to ensure process death
                        "sleep 0.8",
                        // Nudge the system to re-spin anything it needs; do NOT clear data
                        // (no start of a generic launcher activity because VR Shell may be headless)
                        "true"
                });
            } catch (Exception e) {
                Logx.error("Exception while restarting vrshell: " + e);
            }

            Logx.debug("Restart sweep result: " + ok);

            // Give system a moment to respawn services
            try { Thread.sleep(700); } catch (InterruptedException ignored) {}

            if (thenOpenDebug) {
                runOnUiThread(this::openDebugPanel);
            }
        }).start();
    }

    private void openDebugPanel() {
        try {
            // Optional: tell our hooks to close any existing Dogfood activity/panels
            Logx.debug("Killing DogfoodMainActivity (via broadcast), then opening Debug Panel");
            try {
                Intent killDogfood = new Intent(Constants.ACTION_KILL_DOGFOOD);
                killDogfood.setPackage(VR_SHELL_PKG);
                sendBroadcast(killDogfood);
            } catch (Throwable t) {
                Logx.debug("Kill Dogfood broadcast not available: " + t);
            }

            // Optional: broadcast a custom "restart" signal if your hooks listen for it
            try {
                Intent restartSig = new Intent(Constants.ACTION_RESTART_VR_SHELL);
                restartSig.setPackage(VR_SHELL_PKG);
                sendBroadcast(restartSig);
            } catch (Throwable t) {
                Logx.debug("Restart signal broadcast not available: " + t);
            }

            // Launch the Dogfood/Debug activity with the expected extra
            Intent i = new Intent();
            i.setComponent(new ComponentName(VR_SHELL_PKG, DOGFOOD_ACTIVITY));
            i.putExtra("queryParametersUri", "host=debug"); // matches your hook path
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);

            Toast.makeText(this, "Opening Debug Panel…", Toast.LENGTH_SHORT).show();
            Logx.debug("Started " + DOGFOOD_ACTIVITY + " with host=debug");
        } catch (Throwable t) {
            Toast.makeText(this, "Failed to open Debug Panel: " + t, Toast.LENGTH_LONG).show();
            Logx.error("Failed to start debug panel: " + t);
        }
    }

    // === Root helpers ===

    private boolean runSuJoined(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i]);
            if (i != lines.length - 1) sb.append(" ; ");
        }
        return runSu(sb.toString());
    }

    private boolean runSu(String cmd) {
        Process p = null;
        try {
            Logx.debug("su -c >>> " + cmd);
            p = new ProcessBuilder("su", "-c", cmd)
                    .redirectErrorStream(true)
                    .start();
            int code = p.waitFor();
            Logx.debug("su -c exit=" + code);
            return code == 0;
        } catch (Throwable t) {
            Logx.error("runSu failed: " + t);
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }
}
