package com.akylas.enforcedoze;

import android.content.Context;

import java.util.List;

/** Reads, journals, changes and observes device settings on AccessExecutor.SERIAL. */
public final class DeviceController implements RestorationJournal.Device {
    private final AccessExecutor access;
    private final RecoveryStore store;
    private final EvidenceStore evidence;
    private final RestorationJournal journal;

    public DeviceController(Context context) {
        access = new AccessExecutor(context);
        store = new RecoveryStore(context);
        evidence = new EvidenceStore(context);
        journal = new RestorationJournal(store, this, evidence);
    }

    public CommandResult command(String command) {
        return access.run(command);
    }

    public String observe(String query, String kind) {
        return StateParser.read(kind, command(query));
    }

    public interface Undo {
        String command(String original);
    }

    public boolean change(
            String key, String query, String kind, String target, String apply, Undo undo) {
        String original = observe(query, kind);
        if (original == null) {
            evidence.record(
                    "Control unavailable", key + ": cannot read original state; left unchanged");
            return false;
        }
        return journal.apply(
                new RestorationJournal.Entry(
                        key,
                        access.mode(),
                        query,
                        kind,
                        original,
                        target,
                        apply,
                        undo.command(original)));
    }

    public boolean setting(String key, String namespace, String name, String target) {
        String prefix = "settings --user " + (android.os.Process.myUid() / 100000) + " ";
        return change(
                key,
                prefix + "get " + namespace + " " + name,
                "setting",
                target,
                prefix + "put " + namespace + " " + name + " " + CommandResult.quote(target),
                original ->
                        prefix
                                + (original.equals("null")
                                        ? "delete " + namespace + " " + name
                                        : "put "
                                                + namespace
                                                + " "
                                                + name
                                                + " "
                                                + CommandResult.quote(original)));
    }

    public boolean restrictSensors(String exemptPackage) {
        String before = observe("dumpsys sensorservice", "sensor");
        if (!"NORMAL".equals(before)) {
            evidence.record(
                    "Sensor restriction skipped",
                    "Existing SensorService mode: " + before + "; not owned by EnforceDoze");
            return false;
        }
        // Android 16 requires a second argument. An empty substring would exempt every package.
        String allow =
                CommandResult.validPackage(exemptPackage)
                        ? exemptPackage
                        : "!enforcedoze:no-client!";
        return change(
                "Sensor access",
                "dumpsys sensorservice",
                "sensor",
                "RESTRICTED : " + allow,
                "dumpsys sensorservice restrict " + CommandResult.quote(allow),
                ignored -> "dumpsys sensorservice enable");
    }

    public boolean forceIdle() {
        String original = observe("dumpsys deviceidle", "forced");
        if (!"false".equals(original)) {
            evidence.record(
                    "Doze skipped", "Force-idle state is " + original + "; cannot take ownership");
            return false;
        }
        return change(
                "Forced Doze",
                "dumpsys deviceidle",
                "forced",
                "true",
                android.os.Build.VERSION.SDK_INT >= 24
                        ? "dumpsys deviceidle force-idle deep"
                        : "dumpsys deviceidle force-idle",
                ignored -> "dumpsys deviceidle unforce");
    }

    public boolean restoreScreenControls() {
        return journal.restoreMatching(
                e ->
                        e.key.equals("Forced Doze")
                                || e.key.equals("Sensor access")
                                || e.key.equals("Sensor privacy")
                                || e.key.equals("Biometric keyguard"));
    }

    public boolean restore() {
        return journal.restore();
    }

    public boolean maintenance() {
        return journal.restoreExcept("Forced Doze");
    }

    public boolean pending() {
        return store.pending();
    }

    public List<RestorationJournal.Entry> entries() {
        return store.load();
    }

    @Override
    public String read(RestorationJournal.Entry entry) {
        return StateParser.read(entry.kind, access.run(entry.mode, entry.query));
    }

    @Override
    public boolean write(RestorationJournal.Entry entry, boolean restore) throws Exception {
        CommandResult result = access.run(entry.mode, restore ? entry.undo : entry.apply);
        if (!result.ok()) {
            evidence.record(
                    restore ? "Restore command failed" : "Command failed",
                    entry.key + ": " + result.output);
            return false;
        }
        String expected = restore ? entry.original : entry.target;
        // Commands such as Wi-Fi return before the service changes state. Bounded verification
        // only.
        for (int i = 0; i < 4; i++) {
            if (expected.equals(read(entry))) return true;
            if (i < 3) Thread.sleep(250);
        }
        return false;
    }
}
