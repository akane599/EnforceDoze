package com.akylas.enforcedoze;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.annotation.RequiresApi;
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
    @RequiresApi(31)
    static String shell(String command) throws Exception {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        // UiAutomation tokenizes commands without shell quoting. Feed a real shell on stdin.
        var pipes = automation.executeShellCommandRw("/system/bin/sh");
        String marker = "__ENFORCEDOZE_TEST_EXIT__";
        try (var stream = new ParcelFileDescriptor.AutoCloseInputStream(pipes[0]);
                var output = new ByteArrayOutputStream()) {
            try (var input = new ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])) {
                String script = "exec 2>&1\n" + command + "\nprintf '\\n" + marker + "%s\\n' \"$?\"\n";
                input.write(script.getBytes(StandardCharsets.UTF_8));
            }
            byte[] data = new byte[4096];
            int count;
            while ((count = stream.read(data)) != -1) output.write(data, 0, count);
            String result = output.toString(StandardCharsets.UTF_8.name());
            int end = result.lastIndexOf(marker);
            assertTrue("Test shell did not report completion: " + result, end >= 0);
            assertEquals(command + ": " + result, "0", result.substring(end + marker.length()).trim());
            return result.substring(0, end).trim();
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
