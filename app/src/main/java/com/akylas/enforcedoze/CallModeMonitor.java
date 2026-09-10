package com.akylas.enforcedoze;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

/** VoIP calls do not necessarily send TelephonyManager's phone-state broadcast. */
final class CallModeMonitor {
    static AutoCloseable start(Context context, Runnable changed) {
        if (Build.VERSION.SDK_INT < 31) return () -> { };
        return Api31.start(context, changed);
    }

    @RequiresApi(31)
    private static class Api31 {
        static AutoCloseable start(Context context, Runnable changed) {
            AudioManager audio = context.getSystemService(AudioManager.class);
            if (audio == null) return () -> { };
            AudioManager.OnModeChangedListener listener = mode -> changed.run();
            try {
                audio.addOnModeChangedListener(ContextCompat.getMainExecutor(context), listener);
                return () -> audio.removeOnModeChangedListener(listener);
            } catch (RuntimeException e) {
                Utils.logToLogcat("CallModeMonitor", "Audio-mode callbacks unavailable: " + e);
                return () -> { };
            }
        }
    }
}
