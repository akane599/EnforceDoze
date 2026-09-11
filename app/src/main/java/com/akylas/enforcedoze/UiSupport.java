package com.akylas.enforcedoze;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public final class UiSupport {
    public static String statusText(Context context, String state) {
        int res;
        switch (state) {
            case "LOCKED": res = R.string.status_locked; break;
            case "STARTING": res = R.string.status_starting; break;
            case "ACTIVE": res = R.string.status_active; break;
            case "WAITING": res = R.string.status_waiting; break;
            case "DELAYED": res = R.string.status_delayed; break;
            case "ENTERING": res = R.string.status_entering; break;
            case "RESTORING": res = R.string.status_restoring; break;
            case "SCHEDULED": res = R.string.status_scheduled; break;
            case "PAUSED": res = R.string.status_paused; break;
            case "MAINTENANCE": res = R.string.status_maintenance; break;
            case "NEEDS_ACCESS": res = R.string.status_needs_access; break;
            case "NEEDS_START": res = R.string.status_needs_start; break;
            case "RECOVERY": res = R.string.status_recovery; break;
            case "ERROR": res = R.string.status_error; break;
            default: res = R.string.status_off;
        }
        return context.getString(res);
    }
    public static void open(Context context, Intent intent) {
        try { context.startActivity(intent); }
        catch (RuntimeException e) { Toast.makeText(context, R.string.settings_unavailable, Toast.LENGTH_LONG).show(); }
    }
}
