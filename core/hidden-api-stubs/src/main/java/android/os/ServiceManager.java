// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/**
 * Compile-time stub of the hidden {@code android.os.ServiceManager}
 * (AOSP {@code core/java/android/os/ServiceManager.java}, android-16.0.0_r1 and android-17.0.0_r1).
 * Never packaged; the framework class is used at run time.
 */
public final class ServiceManager {
    private ServiceManager() {}

    public static IBinder getService(String name) {
        throw new RuntimeException("Stub!");
    }
}
