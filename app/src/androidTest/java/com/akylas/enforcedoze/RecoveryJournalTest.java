package com.akylas.enforcedoze;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RecoveryJournalTest {
    private Context context;
    @Before public void clear() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("recovery", Context.MODE_PRIVATE).edit().clear().commit();
    }
    @Test public void journalsBeforeMutationAndRecoversAfterRecreation() {
        RecoveryJournal journal = new RecoveryJournal(context, (mode, cmd) -> {
            assertTrue(new RecoveryJournal(context).hasPending());
            return new CommandResult(124, "Simulated lost response after mutation");
        });
        assertFalse(journal.apply("shizuku", "disable-wifi", "restore-wifi"));
        List<String> restored = new ArrayList<>();
        RecoveryJournal recreated = new RecoveryJournal(context, (mode, cmd) -> { restored.add(mode + ":" + cmd); return new CommandResult(0, ""); });
        assertTrue(recreated.restore());
        assertEquals(Arrays.asList("shizuku:restore-wifi"), restored);
        assertFalse(recreated.hasPending());
    }
    @Test public void maintenanceRestoresEnhancementsAndKeepsCoreUndo() {
        List<String> commands = new ArrayList<>();
        RecoveryJournal journal = new RecoveryJournal(context, (mode, cmd) -> { commands.add(cmd); return new CommandResult(0, ""); });
        journal.applyCore("shizuku", "force", "unforce");
        journal.apply("shizuku", "wifi-off", "wifi-on");
        journal.apply("shizuku", "data-off", "data-on");
        commands.clear();
        assertTrue(journal.restoreEnhancements());
        assertEquals(Arrays.asList("data-on", "wifi-on"), commands);
        assertTrue(journal.hasPending());
        commands.clear(); assertTrue(journal.restore());
        assertEquals(Arrays.asList("unforce"), commands);
    }
    @Test public void failedRestorationIsRetriedWithoutRepeatingSuccesses() {
        RecoveryJournal journal = new RecoveryJournal(context, (mode, cmd) -> new CommandResult(cmd.equals("wifi-on") ? -1 : 0, ""));
        journal.apply("shizuku", "wifi-off", "wifi-on"); journal.apply("shizuku", "data-off", "data-on");
        assertFalse(journal.restore());
        List<String> commands = new ArrayList<>();
        RecoveryJournal retried = new RecoveryJournal(context, (mode, cmd) -> { commands.add(cmd); return new CommandResult(0, ""); });
        assertTrue(retried.restore()); assertEquals(Arrays.asList("wifi-on"), commands);
    }
}
