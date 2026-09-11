package com.akylas.enforcedoze;
import android.content.*;
public class AddWhiteListReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!android.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("allowExternalAutomation", false)) return;
        if (!"com.akylas.enforcedoze.ADD_WHITELIST".equals(intent.getAction())) return;
        String pkg = intent.getStringExtra("packageName");
        if (!CommandPolicy.validPackage(pkg)) return;
        PendingResult pending = goAsync();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable finish = () -> { if (finished.compareAndSet(false, true)) pending.finish(); };
        handler.postDelayed(finish, 8000);
        CommandExecutor.execute(context, "dumpsys deviceidle whitelist +" + pkg, result -> { handler.removeCallbacks(finish); finish.run(); });
    }
}
