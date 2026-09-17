package com.akylas.enforcedoze;

import java.util.ArrayList;
import java.util.List;

/** Write-ahead transaction journal. Only callers on the shared device queue may mutate it. */
public final class RestorationJournal {
    public interface Store {
        List<Entry> load();

        boolean save(List<Entry> entries);
    }

    public interface Device {
        String read(Entry entry) throws Exception;

        boolean write(Entry entry, boolean restore) throws Exception;
    }

    public interface Evidence {
        void record(String event, String detail);
    }

    public static final class Entry {
        public final String key, mode, query, kind, original, target, apply, undo;

        public Entry(
                String key,
                String mode,
                String query,
                String kind,
                String original,
                String target,
                String apply,
                String undo) {
            this.key = key;
            this.mode = mode;
            this.query = query;
            this.kind = kind;
            this.original = original;
            this.target = target;
            this.apply = apply;
            this.undo = undo;
        }
    }

    private final Store store;
    private final Device device;
    private final Evidence evidence;

    public RestorationJournal(Store store, Device device, Evidence evidence) {
        this.store = store;
        this.device = device;
        this.evidence = evidence;
    }

    public boolean apply(Entry entry) {
        List<Entry> entries = new ArrayList<>(store.load());
        for (Entry existing : entries) if (existing.key.equals(entry.key)) return false;
        if (entry.original == null || entry.target == null) {
            evidence.record("Skipped", entry.key + ": original state could not be read");
            return false;
        }
        if (entry.original.equals(entry.target)) return true;
        entries.add(entry);
        if (!store.save(entries)) {
            evidence.record(
                    "Failed", entry.key + ": recovery record could not be saved; no command sent");
            return false;
        }
        try {
            if (device.write(entry, false) && entry.target.equals(device.read(entry))) {
                evidence.record(
                        "Applied and observed",
                        entry.key + ": " + entry.original + " → " + entry.target);
                return true;
            }
        } catch (Exception e) {
            evidence.record("Failed", entry.key + ": " + e.getMessage());
        }
        evidence.record("Unconfirmed change", entry.key + ": recovery record retained");
        return false;
    }

    private static int restorePriority(String key) {
        if (key.equals("Forced Doze")) return 100;
        if (key.equals("Sensor privacy")) return 90;
        if (key.equals("Sensor access")) return 80;
        if (key.equals("Biometric keyguard")) return 70;
        if (key.equals("Airplane mode")) return 60;
        return 0;
    }

    public boolean restore() {
        return restoreExcept(null);
    }

    /** During maintenance leave only the forced-idle lease in place. */
    public boolean restoreExcept(String retainedKey) {
        return restoreMatching(e -> !e.key.equals(retainedKey));
    }

    public interface Selection {
        boolean test(Entry entry);
    }

    public boolean restoreMatching(Selection selected) {
        List<Entry> entries = new ArrayList<>(store.load());
        // Release controls that affect unlock/calls before potentially slow per-app recovery.
        java.util.Collections.sort(
                entries, (a, b) -> Integer.compare(restorePriority(a.key), restorePriority(b.key)));
        boolean restored = true;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (!selected.test(entry)) continue;
            try {
                String before = device.read(entry);
                // Unknown access is never interpreted as the original value.
                boolean done = entry.original.equals(before);
                if (!done && before != null)
                    done = device.write(entry, true) && entry.original.equals(device.read(entry));
                if (done) {
                    List<Entry> next = new ArrayList<>(entries);
                    next.remove(i);
                    if (store.save(next)) {
                        entries = next;
                        evidence.record("Restored and observed", entry.key + ": " + entry.original);
                        continue;
                    }
                }
            } catch (Exception e) {
                evidence.record("Restore failed", entry.key + ": " + e.getMessage());
            }
            restored = false;
            evidence.record(
                    "Restoration pending",
                    entry.key
                            + " → "
                            + entry.original
                            + ". Reconnect "
                            + entry.mode
                            + " and retry.");
        }
        return restored;
    }
}
