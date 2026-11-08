// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.api;

import io.github.akane599.enforcedoze.privilege.api.CommandOutcome;

/**
 * The privileged worker (SPEC 4.3). It runs from this APK in a separate process as shell (uid 2000)
 * or root, started by Shizuku, Sui or libsu. Typed methods only: every argument is validated in the
 * worker and turned into a fixed argument list; nothing is passed to a shell. Every call checks the
 * caller's uid. Large outputs are written to the given pipe, never returned through binder; the
 * worker closes the pipe with an error when the command fails. See WorkerContract for the accepted
 * values.
 */
interface IDozeWorker {
    /** Shizuku's UserService contract: the server sends this transaction to stop the service. */
    void destroy() = 16777114;

    /** WorkerContract.VERSION of the APK the worker was loaded from. */
    int getVersion() = 1;

    /** uid of the worker process: 0 for root, 2000 for shell. */
    int getUid() = 2;

    // 10-12 (device_config put, delete and reset in device_idle) were removed in VERSION 2 and are
    // never reused: shell may not write those keys on API 36 and 37, and the app writes the global
    // device_idle_constants string itself with WRITE_SECURE_SETTINGS (ADR 0012).

    /** cmd deviceidle force-idle MODE, MODE being "deep" or "light". */
    CommandOutcome forceIdle(String mode) = 13;

    /** cmd deviceidle unforce. */
    CommandOutcome unforce() = 14;

    /** cmd deviceidle get WHICH: deep, light, force, screen or charging. */
    CommandOutcome getIdleState(String which) = 15;

    /** dumpsys sensorservice restrict PACKAGE, or enable when the package is null. */
    CommandOutcome setSensorRestriction(String allowPackage) = 16;

    /** All-sensors privacy toggle through the hidden ISensorPrivacyManager. */
    CommandOutcome setAllSensorsOff(boolean off) = 17;

    /** Current all-sensors privacy state; stdout is "true" or "false". */
    CommandOutcome getAllSensorsOff() = 18;

    /** cmd deviceidle whitelist +PACKAGE or -PACKAGE. */
    CommandOutcome allowlist(String packageName, boolean add) = 19;

    /** pm grant <this app> PERMISSION, for the durable grants of tier T1 only. */
    CommandOutcome grantSelf(String permission) = 20;

    /** appops set <this app> OP MODE. */
    CommandOutcome setSelfAppOp(String op, String mode) = 21;

    /** settings get global device_idle_constants; stdout is the string, or "null" when unset. */
    CommandOutcome getDeviceIdleSettings() = 22;

    /** device_config list device_idle; stdout is key=value lines, empty when nothing is set. */
    CommandOutcome listDeviceIdleConfig() = 23;

    /** dumpsys deviceidle */
    void readDeviceIdle(in ParcelFileDescriptor pipe) = 30;

    /** dumpsys sensorservice */
    void readSensorService(in ParcelFileDescriptor pipe) = 31;

    /** logcat -b events -d -v epoch, from sinceMillis (wall clock) when it is above 0. */
    void readEventLog(long sinceMillis, in ParcelFileDescriptor pipe) = 32;

    /** dumpsys batterystats --history-start historyStart (0 for the first read); ends with NEXT: (ADR 0018). */
    void readBatteryHistory(long historyStart, in ParcelFileDescriptor pipe) = 33;

    /** cmd deviceidle whitelist */
    void readAllowlist(in ParcelFileDescriptor pipe) = 34;

    /** dumpsys alarm; its Alarm Stats counters, read at screen off and screen on (ADR 0019). */
    void readAlarms(in ParcelFileDescriptor pipe) = 35;
}
