// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/**
 * Compile-time stub of the hidden AIDL interface
 * {@code core/java/android/hardware/ISensorPrivacyManager.aidl}. Both methods exist unchanged at
 * android-16.0.0_r1 and android-17.0.0_r1: {@code boolean isSensorPrivacyEnabled();} and
 * {@code void setSensorPrivacy(boolean enable);}. Only the methods the worker calls are declared; the
 * transaction codes come from the framework's generated class at run time.
 */
public interface ISensorPrivacyManager extends IInterface {
    boolean isSensorPrivacyEnabled() throws RemoteException;

    void setSensorPrivacy(boolean enable) throws RemoteException;

    abstract class Stub extends android.os.Binder implements ISensorPrivacyManager {
        public static ISensorPrivacyManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
