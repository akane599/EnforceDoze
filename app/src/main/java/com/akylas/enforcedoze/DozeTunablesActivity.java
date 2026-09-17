package com.akylas.enforcedoze;

import android.content.*;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;

/** Edits one supported constant at a time; stores originals separately from screen-off leases. */
public class DozeTunablesActivity extends BaseActivity {
    private TextView status;
    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle); screen("Doze tunables",true);
        card("Advanced timing controls","Values affect Android globally and persist until restored here. Stop monitoring first. Only constants listed by this device are offered for changes. Android or the OEM may clamp values; stored values alone do not prove a battery benefit.");
        status=text(body,"Choose a value to inspect its stored setting.\n"+new RecoveryStore(this,"tunable_recovery").summary(),15,false);
        button(body,"Restore previous tunables",()->work(true,null,null));
        ArrayList<String> keys=new ArrayList<>(Arrays.asList(DozeTunableConstants.KEY_LIGHT_IDLE_AFTER_INACTIVE_TIMEOUT,DozeTunableConstants.KEY_LIGHT_PRE_IDLE_TIMEOUT,DozeTunableConstants.KEY_LIGHT_IDLE_TIMEOUT,DozeTunableConstants.KEY_LIGHT_IDLE_FACTOR,DozeTunableConstants.KEY_LIGHT_MAX_IDLE_TIMEOUT,DozeTunableConstants.KEY_LIGHT_IDLE_MAINTENANCE_MIN_BUDGET,DozeTunableConstants.KEY_LIGHT_IDLE_MAINTENANCE_MAX_BUDGET,DozeTunableConstants.KEY_MIN_LIGHT_MAINTENANCE_TIME,DozeTunableConstants.KEY_MIN_DEEP_MAINTENANCE_TIME,DozeTunableConstants.KEY_INACTIVE_TIMEOUT,DozeTunableConstants.KEY_SENSING_TIMEOUT,DozeTunableConstants.KEY_LOCATING_TIMEOUT,DozeTunableConstants.KEY_LOCATION_ACCURACY,DozeTunableConstants.KEY_MOTION_INACTIVE_TIMEOUT,DozeTunableConstants.KEY_IDLE_AFTER_INACTIVE_TIMEOUT,DozeTunableConstants.KEY_IDLE_PENDING_TIMEOUT,DozeTunableConstants.KEY_MAX_IDLE_PENDING_TIMEOUT,DozeTunableConstants.KEY_IDLE_PENDING_FACTOR,DozeTunableConstants.KEY_IDLE_TIMEOUT,DozeTunableConstants.KEY_MAX_IDLE_TIMEOUT,DozeTunableConstants.KEY_IDLE_FACTOR,DozeTunableConstants.KEY_MIN_TIME_TO_ALARM,DozeTunableConstants.KEY_MAX_TEMP_APP_WHITELIST_DURATION,DozeTunableConstants.KEY_MMS_TEMP_APP_WHITELIST_DURATION,DozeTunableConstants.KEY_SMS_TEMP_APP_WHITELIST_DURATION,DozeTunableConstants.KEY_NOTIFICATION_WHITELIST_DURATION));
        Collections.sort(keys);
        for(String key:keys) button(body,key,()->inspect(key));
    }
    private boolean editable() {
        if(android.preference.PreferenceManager.getDefaultSharedPreferences(this).getBoolean("serviceEnabled",false)||new RecoveryStore(this).pending()) {
            message("Stop and restore first","Stop monitoring and finish pending restoration before changing Android timing values."); return false;
        }
        return true;
    }
    private void inspect(String key) {
        if(!editable()) return;
        status.setText("Reading "+key+"…");
        AccessExecutor.SERIAL.execute(()->{
            AccessExecutor access=new AccessExecutor(this);
            CommandResult dump=access.run("dumpsys deviceidle");
            boolean supported=dump.ok()&&java.util.regex.Pattern.compile("(?m)^\\s*"+java.util.regex.Pattern.quote(key)+"=").matcher(dump.output).find();
            String query=Build.VERSION.SDK_INT>=34?"device_config get device_idle "+key:"settings get global device_idle_constants";
            CommandResult value=access.run(query);
            runOnUiThread(()->{
                if(isDestroyed()) return;
                if(!supported||!value.ok()) { status.setText(key+" is not exposed by this device; no change made."); return; }
                EditText input=new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); input.setPadding(dp(20),dp(12),dp(20),dp(12));
                if(Build.VERSION.SDK_INT>=34&&!value.output.equals("null")) input.setText(value.output);
                androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(key).setMessage("Current stored value: "+value.output+"\nTimeouts: milliseconds. Factors: 1–10. Accuracy: meters. Original values are retained for Restore previous tunables.")
                        .setView(input).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();
                dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
                    String proposed=input.getText().toString().trim(); if(!valid(key,proposed)) { input.setError("Enter a finite valid value (timeout: 0–604800000; factor: 1–10)"); return; }
                    dialog.dismiss(); work(false,key,proposed);
                })); dialog.show();
            });
        });
    }
    public static boolean valid(String key,String value) {
        if(value==null||!value.matches("[0-9]+(?:\\.[0-9]+)?")) return false;
        try { double n=Double.parseDouble(value); if(Double.isNaN(n)||Double.isInfinite(n)) return false;
            return key.endsWith("factor") ? n>=1&&n<=10 : key.equals("location_accuracy")?n>=0&&n<=10000 : n>=0&&n<=604800000&&n==Math.rint(n);
        } catch(NumberFormatException e) { return false; }
    }
    private void work(boolean restore,String key,String value) {
        if(!editable()) return; status.setText(restore?"Restoring previous values…":"Applying and verifying…");
        AccessExecutor.SERIAL.execute(()->{
            RecoveryStore store=new RecoveryStore(this,"tunable_recovery"); AccessExecutor access=new AccessExecutor(this); EvidenceStore evidence=new EvidenceStore(this);
            RestorationJournal journal=TunableRecovery.journal(this);
            boolean ok=false;
            try {
                if(restore) ok=journal.restore();
                else {
                    String query,apply,undo,target=value,id=key;
                    if(Build.VERSION.SDK_INT>=34) {
                        query="device_config get device_idle "+key;
                        String original=StateParser.read("setting",access.run(query));
                        apply="device_config put device_idle "+key+" "+CommandResult.quote(value);
                        undo=original==null?"":original.equals("null")?"device_config delete device_idle "+key:"device_config put device_idle "+key+" "+CommandResult.quote(original);
                        ok=journal.apply(new RestorationJournal.Entry(id,access.mode(),query,"setting",original,target,apply,undo));
                    } else {
                        query="settings get global device_idle_constants"; String original=StateParser.read("setting",access.run(query));
                        if(original!=null) {
                            LinkedHashMap<String,String> map=new LinkedHashMap<>();
                            if(!original.equals("null")) for(String pair:original.split(",")) { String[] p=pair.split("=",2); if(p.length==2) map.put(p[0],p[1]); }
                            map.put(key,value); StringBuilder combined=new StringBuilder(); for(Map.Entry<String,String> pair:map.entrySet()) { if(combined.length()>0) combined.append(','); combined.append(pair.getKey()).append('=').append(pair.getValue()); }
                            target=combined.toString(); apply="settings put global device_idle_constants "+CommandResult.quote(target);
                            undo=original.equals("null")?"settings delete global device_idle_constants":"settings put global device_idle_constants "+CommandResult.quote(original);
                            ok=journal.apply(new RestorationJournal.Entry("Legacy tunables",access.mode(),query,"setting",original,target,apply,undo));
                        }
                    }
                }
            } catch(Exception e) { evidence.record("Tunable change failed",e.toString()); }
            boolean done=ok;
            runOnUiThread(()->{ if(!isDestroyed()) status.setText(done?(restore?"Previous tunables restored and read back.":"Stored value verified. Use Restore previous tunables before editing this value again.") : "Not verified. Check access and diagnostics. Saved original values remain available for restoration.\n"+store.summary()); });
        });
    }
}
