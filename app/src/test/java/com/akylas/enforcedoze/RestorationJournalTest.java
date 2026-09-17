package com.akylas.enforcedoze;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RestorationJournalTest {
    static class Memory implements RestorationJournal.Store {
        List<RestorationJournal.Entry> data = new ArrayList<>();
        boolean writable = true;
        public List<RestorationJournal.Entry> load() { return new ArrayList<>(data); }
        public boolean save(List<RestorationJournal.Entry> next) {
            if (!writable) return false;
            data = new ArrayList<>(next); return true;
        }
    }
    static class Device implements RestorationJournal.Device {
        String state = "1";
        boolean access = true, lie, failAfterWrite;
        int writes;
        Memory store;
        Device(Memory store) { this.store = store; }
        public String read(RestorationJournal.Entry e) { return access ? state : null; }
        public boolean write(RestorationJournal.Entry e, boolean restore) throws Exception {
            assertFalse("Journal must be durable before touching device", store.data.isEmpty());
            writes++;
            if (!access) return false;
            if (!lie) state = restore ? e.original : e.target;
            if (failAfterWrite) { access = false; throw new Exception("binder died after mutation"); }
            return true;
        }
    }
    RestorationJournal.Entry change() {
        return new RestorationJournal.Entry("wifi", "shizuku", "query", "switch", "1", "0", "off", "on");
    }
    RestorationJournal journal(Memory m, Device d) { return new RestorationJournal(m, d, (a,b) -> {}); }
    @Test public void persistsBeforeWriteAndConfirmsRestoration() {
        Memory m = new Memory(); Device d = new Device(m);
        assertTrue(journal(m,d).apply(change())); assertEquals("0", d.state);
        assertEquals(1,m.data.size());
        assertTrue(journal(m,d).restore()); assertEquals("1", d.state); assertTrue(m.data.isEmpty());
    }
    @Test public void crashAfterMutationRecoversInNewInstance() {
        Memory m = new Memory(); Device d = new Device(m); d.failAfterWrite = true;
        assertFalse(journal(m,d).apply(change()));
        assertFalse(journal(m,d).restore()); assertEquals(1,m.data.size());
        d.access = true; d.failAfterWrite = false;
        assertTrue(journal(m,d).restore()); assertEquals("1",d.state);
    }
    @Test public void successfulCommandIsNotProof() {
        Memory m = new Memory(); Device d = new Device(m); d.lie = true;
        assertFalse(journal(m,d).apply(change())); assertEquals(1,m.data.size());
        assertTrue(journal(m,d).restore()); assertEquals(1,d.writes);
    }
    @Test public void cannotMutateWhenPersistenceFails() {
        Memory m = new Memory(); m.writable = false; Device d = new Device(m);
        assertFalse(journal(m,d).apply(change())); assertEquals(0,d.writes);
    }
    @Test public void changingOptionsDoesNotLoseOriginal() {
        Memory m = new Memory(); Device d = new Device(m);
        assertTrue(journal(m,d).apply(change()));
        assertFalse(journal(m,d).apply(new RestorationJournal.Entry("wifi", "root", "", "", "0", "1", "", "")));
        assertEquals("1",m.data.get(0).original); assertEquals("shizuku",m.data.get(0).mode);
    }
    @Test public void failedRestoreRetainsRecord() {
        Memory m = new Memory(); Device d = new Device(m); assertTrue(journal(m,d).apply(change()));
        d.lie = true; assertFalse(journal(m,d).restore()); assertEquals(1,m.data.size());
        d.lie = false; assertTrue(journal(m,d).restore());
    }
    @Test public void unchangedStateNeedsNoUndo() {
        Memory m = new Memory(); Device d = new Device(m);
        assertTrue(journal(m,d).apply(new RestorationJournal.Entry("wifi", "shizuku", "", "", "0", "0", "", "")));
        assertTrue(m.data.isEmpty()); assertEquals(0,d.writes);
    }
}
