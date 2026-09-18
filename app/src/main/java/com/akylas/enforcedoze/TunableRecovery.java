package com.akylas.enforcedoze;

import android.content.Context;

/** Persistent timing edits have an independent undo journal, including across option resets. */
final class TunableRecovery {
    static RestorationJournal journal(Context context) {
        AccessExecutor access = new AccessExecutor(context);
        return new RestorationJournal(
                new RecoveryStore(context, "tunable_recovery"),
                new RestorationJournal.Device() {
                    public String read(RestorationJournal.Entry entry) {
                        return StateParser.read("setting", access.run(entry.mode, entry.query));
                    }

                    public boolean write(RestorationJournal.Entry entry, boolean undo) {
                        return access.run(entry.mode, undo ? entry.undo : entry.apply).ok();
                    }
                },
                new EvidenceStore(context));
    }

    static boolean restore(Context context) {
        return journal(context).restore();
    }
}
