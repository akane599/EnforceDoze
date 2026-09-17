package com.akylas.enforcedoze;

import android.Manifest;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;

public class AccessActivity extends BaseActivity {
    private TextView status;
    private final ShizukuHandler.OnAvailibilityChange listener=available->refresh();
    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle); screen("Access & permissions",true);
        LinearLayout card=card("Privileged access","Shizuku is recommended on Android 16. Start it in the Shizuku app, authorize EnforceDoze, then verify the connection.");
        status=text(card,"",16,false); status.setId(R.id.access_status);
        button(card,"Choose access mode",()->{
            String[] values={"shizuku","root","nonroot"};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Access mode")
                    .setItems(new String[]{"Shizuku (recommended)","Root","ADB-granted app access (legacy)"},(d,index)->{
                        if(new RecoveryStore(this).pending()) { message("Restore first","Temporary changes belong to the previous access mode. Restore them before switching."); return; }
                        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("executionMode",values[index]).apply();
                        SettingsActivity.reloadSettings(this); refresh();
                    }).show();
        });
        button(card,"Open Shizuku",()->{
            Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if(launch!=null) safeStart(launch); else safeStart(new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/")));
        });
        button(card,"Authorize Shizuku",()->ShizukuHandler.getInstance(this).requestShizukuPermission()).setId(R.id.authorize_shizuku);
        button(card,"Verify access",()->{
            status.setText("Verifying access…");
            AccessExecutor.SERIAL.execute(()->{
                CommandResult r=new AccessExecutor(this).run("id -u");
                String result=r.ok()?"Command identity: uid "+r.output+". Each device control is verified separately.":r.output;
                new EvidenceStore(this).record("Access verification",result);
                runOnUiThread(()->{ if(!isDestroyed()) status.setText(result); });
            });
        });
        button(card,"Check or enable Android Doze",()->{
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Enable Android Doze?")
                    .setMessage("This enables Android’s own light and deep idle controllers if disabled. It does not force a session or prove this OEM supports every Doze behavior.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Enable & verify",(d,w)->AccessExecutor.SERIAL.execute(()->{
                        AccessExecutor access=new AccessExecutor(this);
                        CommandResult result=access.run(android.os.Build.VERSION.SDK_INT>=24?"dumpsys deviceidle enable all":"dumpsys deviceidle enable");
                        CommandResult read=access.run("dumpsys deviceidle");
                        boolean enabled=result.ok()&&read.ok()&&read.output.contains("mDeepEnabled=true");
                        new EvidenceStore(this).record("Android Doze enable",enabled?"Deep controller enabled, observed in dumpsys":"Not verified: "+result.output);
                        runOnUiThread(()->{ if(!isDestroyed()) status.setText(enabled?"Android’s Deep Doze controller is enabled.":"Not verified. Check diagnostics and access."); });
                    })).show();
        });
        LinearLayout permissions=card("Keep recovery visible","Allow notifications so access failures and pending restoration remain visible. Phone permission lets monitoring pause for calls.");
        button(permissions,"Allow notifications & call protection",()->{
            ArrayList<String> list=new ArrayList<>();
            if(Build.VERSION.SDK_INT>=33&&!Utils.isPostNotificationPermissionGranted(this)) list.add(Manifest.permission.POST_NOTIFICATIONS);
            if(!Utils.isReadPhoneStatePermissionGranted(this)) list.add(Manifest.permission.READ_PHONE_STATE);
            if(!list.isEmpty()) ActivityCompat.requestPermissions(this,list.toArray(new String[0]),20);
            else message("Permissions ready","Notifications and call protection are available.");
        });
        button(permissions,"Battery optimization settings",()->safeStart(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        button(permissions,"Notification access for media protection",()->safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(permissions,"Allow precise schedule alarms",()->{
            if(Build.VERSION.SDK_INT>=31) safeStart(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));
            else message("Available","This Android version allows exact alarms without additional access.");
        });
        card("Legacy ADB access","ADB-granted DUMP and WRITE_SECURE_SETTINGS permissions can support older Android versions. On Android 14+, these permissions do not grant device_config or force-idle access. Use Shizuku or root for automatic Deep Doze. Never assume a permission grant means a feature succeeded.");
        button(body,"Copy legacy ADB setup commands",()->copy("ADB setup","adb shell pm grant "+getPackageName()+" android.permission.DUMP\nadb shell pm grant "+getPackageName()+" android.permission.WRITE_SECURE_SETTINGS"));
    }
    private void refresh() { if(status!=null) status.setText("Mode: "+new AccessExecutor(this).mode()+"\n"+ShizukuHandler.getInstance(this).status()); }
    @Override protected void onStart() { super.onStart(); ShizukuHandler.getInstance(this).addListener(listener); refresh(); }
    @Override protected void onStop() { ShizukuHandler.getInstance(this).removeListener(listener); super.onStop(); }
}
