package com.akylas.enforcedoze;

import android.content.Context;
import android.media.AudioManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 31)
public class CallModeMonitorTest {
    @Test public void voipAudioModeChangeIsObservedWithoutPhoneBroadcast() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        AudioManager audio = context.getSystemService(AudioManager.class);
        int original = audio.getMode();
        CountDownLatch callback = new CountDownLatch(1);
        automation.adoptShellPermissionIdentity();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
                AutoCloseable monitor = CallModeMonitor.start(context, () -> {
                    if (Utils.isUserInCommunicationCall(context)) callback.countDown();
                })) {
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            assertTrue("VoIP mode change was not observed", callback.await(5, TimeUnit.SECONDS));
            assertTrue(Utils.isUserInCommunicationCall(context));
        } finally {
            audio.setMode(original);
            automation.dropShellPermissionIdentity();
        }
    }
}
