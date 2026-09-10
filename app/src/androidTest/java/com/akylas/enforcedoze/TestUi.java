package com.akylas.enforcedoze;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import static org.junit.Assert.*;

final class TestUi {
    static void await(String message, Callable<Boolean> condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition.call()) return;
            SystemClock.sleep(50);
        }
        fail(message);
    }
    static String shell(String command) throws Exception {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try (var stream = new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command));
                var output = new ByteArrayOutputStream()) {
            byte[] data = new byte[4096];
            int count;
            while ((count = stream.read(data)) != -1) output.write(data, 0, count);
            return output.toString(StandardCharsets.UTF_8.name()).trim();
        }
    }
    static void screenshot(String name) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();
        instrumentation.getUiAutomation().waitForIdle(1000, 10000);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "screenshots");
        assertTrue(directory.exists() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
