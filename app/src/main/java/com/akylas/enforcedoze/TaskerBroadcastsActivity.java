package com.akylas.enforcedoze;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.LinearLayout;
import com.google.android.material.materialswitch.MaterialSwitch;
public class TaskerBroadcastsActivity extends BaseActivity {
    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle); screen("Tasker automation",true);
        LinearLayout access=card("Allow trusted automations","Each broadcast requires an automationToken string extra. Keep this token private: it authorizes the controls below. Existing automations must add it after upgrading.");
        MaterialSwitch enabled=new MaterialSwitch(this); enabled.setText("Allow automation broadcasts"); enabled.setMinHeight(dp(56));
        enabled.setChecked(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("allowAutomation",false)); access.addView(enabled);
        enabled.setOnCheckedChangeListener((v,value)->{ Automation.token(this); PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("allowAutomation",value).apply(); });
        button(access,"Copy automation token",()->copy("EnforceDoze automation token",Automation.token(this)));
        button(access,"Replace token",()->new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Replace token?").setMessage("Existing automations will need the new token.").setNegativeButton("Cancel",null).setPositiveButton("Replace",(d,w)->{
            PreferenceManager.getDefaultSharedPreferences(this).edit().remove("automationToken").apply(); Automation.token(this);
        }).show());
        card("Tasker Send Intent","Target: Broadcast Receiver\nPackage: com.akylas.enforcedoze\nExtra for every action: automationToken:<your token>\n\nAndroid may restrict starting monitoring from a background broadcast. Open the app if Diagnostics reports a blocked start.");
        for(String action:new String[]{"ENABLE_FORCEDOZE","DISABLE_FORCEDOZE","ADD_WHITELIST","REMOVE_WHITELIST","CHANGE_SETTING"}) {
            LinearLayout item=card(action,action.contains("WHITELIST")?"Extra: packageName:com.example.app":action.equals("CHANGE_SETTING")?"Extras: settingName:<key> and settingValue:<value>":"No additional extras.");
            button(item,"Copy action",()->copy("Action","com.akylas.enforcedoze."+action));
        }
        card("Supported setting keys","dozeEnterDelay: integer seconds, 0–1800\n\nBoolean values must be true or false:\nignoreIfHotspot\nturnOffDataInDoze\nturnOffWiFiInDoze\nignoreLockscreenTimeout\ndisableMotionSensors\ndisableWhenCharging\nshowPersistentNotif\nwaitForUnlock\nturnOnBatterySaverInDoze\nwhitelistMusicAppNetwork");
    }
}
