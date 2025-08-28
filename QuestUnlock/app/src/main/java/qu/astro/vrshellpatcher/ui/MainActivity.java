package qu.astro.vrshellpatcher.ui;

import static android.os.Process.killProcess;
import qu.astro.vrshellpatcher.util.Constants;
import qu.astro.vrshellpatcher.util.Logx;


import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;


public class MainActivity extends Activity implements View.OnClickListener, DialogInterface.OnClickListener {


    Button startBtn, aboutBtn;
    LinearLayout CreateLayout() {
        LinearLayout layout = new LinearLayout(this);

        layout.setOrientation(LinearLayout.VERTICAL);

        startBtn = new Button(this);
        startBtn.setText("Open debug panel");
        startBtn.setOnClickListener(this);
        aboutBtn = new Button(this);
        aboutBtn.setText("About");
        aboutBtn.setOnClickListener(this);

        layout.addView(startBtn, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(aboutBtn, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        return layout;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(CreateLayout());
    }

    @Override
    public void onClick(View view) {
        if (view == aboutBtn) {
            var builder = new AlertDialog.Builder(this).setTitle("About").setMessage("Xposed module for patching vrshell and allowing to use debug stuff\nCredits:\nxAstroBoy\nGeoKar");
            builder.setNeutralButton("OK", this);
            builder.show();
            return;
        }

        Logx.debug("Killing DogfoodMainActivity");
        Intent intent = new Intent(Constants.ACTION_KILL_DOGFOOD);
        intent.setPackage("com.oculus.vrshell");
        sendBroadcast(intent);

        Logx.debug("Starting Debug Panel Activity Hijack");
        intent = new Intent();
        intent.setComponent(new ComponentName("com.oculus.vrshell", "com.oculus.panelapp.dogfood.DogfoodMainActivity"));
        intent.putExtra("queryParametersUri", "host=debug");
        startActivity(intent);
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {}
}