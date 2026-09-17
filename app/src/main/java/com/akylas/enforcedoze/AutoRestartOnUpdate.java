package com.akylas.enforcedoze;

import android.content.*;

public class AutoRestartOnUpdate extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))
            BootCompleteReceiver.recover(c);
    }
}
