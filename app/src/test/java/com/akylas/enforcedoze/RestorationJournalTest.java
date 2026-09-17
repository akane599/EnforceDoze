package com.akylas.enforcedoze;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public class RestorationJournalTest {
    static class Memory implements RestorationJournal.Store {
        List<RestorationJournal.Entry> data = new ArrayList<>();
        boolean writable = true;

        public List<RestorationJournal.Entry> load() {
            return new ArrayList<>(data);
        }

        public boolean save(List<RestorationJournal.Entry> next) {
            if (!writable) return false;
            data = new ArrayList<>(next);
            return true;
        }
    }

    static class Device implements RestorationJournal.Device {
        String state = "1";
        boolean access = true, lie, failAfterWrite;
        int writes;
        Memory store;

        Device(Memory store) {
            this.store = store;
        }

        public String read(RestorationJournal.Entry e) {
            return access ? state : null;
        }

        public boolean write(RestorationJournal.Entry e, boolean restore) throws Exception {
            assertFalse("Journal must be durable before touching device", store.data.isEmpty());
            writes++;
            if (!access) return false;
            if (!lie) state = restore ? e.original : e.target;
            if (failAfterWrite) {
                access = false;
                throw new Exception("binder died after mutation");
            }
            return true;
        }
    }

    RestorationJournal.Entry change() {
        return new RestorationJournal.Entry(
                "wifi", "shizuku", "query", "switch", "1", "0", "off", "on");
    }

    RestorationJournal journal(Memory m, Device d) {
        return new RestorationJournal(m, d, (a, b) -> {});
    }

    @Test
    public void persistsBeforeWriteAndConfirmsRestoration() {
        Memory m = new Memory();
        Device d = new Device(m);
        assertTrue(journal(m, d).apply(change()));
        assertEquals("0", d.state);
        assertEquals(1, m.data.size());
        assertTrue(journal(m, d).restore());
        assertEquals("1", d.state);
        assertTrue(m.data.isEmpty());
    }

    @Test
    public void crashAfterMutationRecoversInNewInstance() {
        Memory m = new Memory();
        Device d = new Device(m);
        d.failAfterWrite = true;
        assertFalse(journal(m, d).apply(change()));
        assertFalse(journal(m, d).restore());
        assertEquals(1, m.data.size());
        d.access = true;
        d.failAfterWrite = false;
        assertTrue(journal(m, d).restore());
        assertEquals("1", d.state);
    }

    @Test
    public void successfulCommandIsNotProof() {
        Memory m = new Memory();
        Device d = new Device(m);
        d.lie = true;
        assertFalse(journal(m, d).apply(change()));
        assertEquals(1, m.data.size());
        assertTrue(journal(m, d).restore());
        assertEquals(1, d.writes);
    }

    @Test
    public void cannotMutateWhenPersistenceFails() {
        Memory m = new Memory();
        m.writable = false;
        Device d = new Device(m);
        assertFalse(journal(m, d).apply(change()));
        assertEquals(0, d.writes);
    }

    @Test
    public void changingOptionsDoesNotLoseOriginal() {
        Memory m = new Memory();
        Device d = new Device(m);
        assertTrue(journal(m, d).apply(change()));
        assertFalse(
                journal(m, d)
                        .apply(
                                new RestorationJournal.Entry(
                                        "wifi", "root", "", "", "0", "1", "", "")));
        assertEquals("1", m.data.get(0).original);
        assertEquals("shizuku", m.data.get(0).mode);
    }

    @Test
    public void failedRestoreRetainsRecord() {
        Memory m = new Memory();
        Device d = new Device(m);
        assertTrue(journal(m, d).apply(change()));
        d.lie = true;
        assertFalse(journal(m, d).restore());
        assertEquals(1, m.data.size());
        d.lie = false;
        assertTrue(journal(m, d).restore());
    }

    @Test
    public void unchangedStateNeedsNoUndo() {
        Memory m = new Memory();
        Device d = new Device(m);
        assertTrue(
                journal(m, d)
                        .apply(
                                new RestorationJournal.Entry(
                                        "wifi", "shizuku", "", "", "0", "0", "", "")));
        assertTrue(m.data.isEmpty());
        assertEquals(0, d.writes);
    }

    @Test
    public void unavailableOriginalCannotBeApplied() {
        Memory m = new Memory();
        Device d = new Device(m);
        assertFalse(
                journal(m, d)
                        .apply(
                                new RestorationJournal.Entry(
                                        "wifi", "shizuku", "", "", null, "0", "off", "on")));
        assertEquals(0, d.writes);
        assertTrue(m.data.isEmpty());
    }

    @Test
    public void releaseUnlockAndCallControlsBeforeAppsAndKeepFailedUndo() {
        Memory m = new Memory();
        List<String> restored = new ArrayList<>();
        for (String key :
                new String[] {
                    "Forced Doze",
                    "Sensor access",
                    "Sensor privacy",
                    "Airplane mode",
                    "App example.pkg"
                })
            m.data.add(
                    new RestorationJournal.Entry(
                            key,
                            "shizuku",
                            "query",
                            "kind",
                            "original",
                            "target",
                            "apply",
                            "undo"));
        RestorationJournal.Device backend =
                new RestorationJournal.Device() {
                    public String read(RestorationJournal.Entry e) {
                        return restored.contains(e.key) ? "original" : "target";
                    }

                    public boolean write(RestorationJournal.Entry e, boolean undo) {
                        if (e.key.startsWith("App")) return false;
                        restored.add(e.key);
                        return true;
                    }
                };
        assertFalse(new RestorationJournal(m, backend, (a, b) -> {}).restore());
        assertEquals(
                Arrays.asList("Forced Doze", "Sensor privacy", "Sensor access", "Airplane mode"),
                restored);
        assertEquals(1, m.data.size());
        assertEquals("App example.pkg", m.data.get(0).key);
    }

    @Test
    public void maintenanceRestoresOptionsButKeepsForcedLease() {
        Memory m = new Memory();
        Device d = new Device(m);
        m.data.add(
                new RestorationJournal.Entry(
                        "Forced Doze", "shizuku", "query", "kind", "1", "0", "off", "on"));
        m.data.add(change());
        d.state = "0";
        assertTrue(journal(m, d).restoreExcept("Forced Doze"));
        assertEquals(1, m.data.size());
        assertEquals("Forced Doze", m.data.get(0).key);
    }

    @Test
    public void persistenceFailureAfterRestoreKeepsUndoForNextAttempt() {
        Memory m = new Memory();
        Device d = new Device(m);
        assertTrue(journal(m, d).apply(change()));
        m.writable = false;
        assertFalse(journal(m, d).restore());
        assertEquals("1", d.state);
        assertEquals(1, m.data.size());
        m.writable = true;
        assertTrue(journal(m, d).restore());
        assertEquals(2, d.writes);
        assertTrue(m.data.isEmpty());
    }
}
