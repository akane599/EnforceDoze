package com.akylas.enforcedoze;

import android.content.*;

public class RemoveWhiteListReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Automation.whitelist(this, c, i, false);
    }
}
