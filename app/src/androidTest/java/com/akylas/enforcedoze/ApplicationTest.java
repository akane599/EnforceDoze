package com.akylas.enforcedoze;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ApplicationTest {
    @Test public void privateJournalStartsReadable() {
        RecoveryStore store = new RecoveryStore(InstrumentationRegistry.getInstrumentation().getTargetContext());
        assertNotNull(store.load());
    }
}
