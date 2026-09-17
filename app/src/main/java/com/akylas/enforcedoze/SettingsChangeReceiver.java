package com.akylas.enforcedoze;
import android.content.*;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
public class SettingsChangeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(!Automation.allowed(c,i)) return;
        String key=i.getStringExtra("settingName"), value=i.getStringExtra("settingValue");
        if(key==null || value==null || !Utils.doesSettingExist(key)) return;
        if("dozeEnterDelay".equals(key)) {
            try { int seconds=Integer.parseInt(value); if(seconds<0 || seconds>1800) return; Utils.updateSettingInt(c,key,seconds); }
            catch(NumberFormatException e) { return; }
        } else {
            if(!value.equals("true") && !value.equals("false")) return;
            Utils.updateSettingBool(c,key,Boolean.parseBoolean(value));
        }
        LocalBroadcastManager.getInstance(c).sendBroadcast(new Intent("reload-settings"));
    }
}
