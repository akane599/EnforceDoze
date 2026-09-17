package com.akylas.enforcedoze;

import android.content.*;

public class AddWhiteListReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Automation.whitelist(this, c, i, true);
    }
}
