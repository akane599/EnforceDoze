package com.akylas.enforcedoze;

import android.os.IBinder;
import androidx.annotation.Keep;
import org.json.JSONObject;

/** Lives in Shizuku's shell/root process; hidden interfaces resolved by name, never transaction ID. */
@Keep
public class PrivilegedService extends IPrivilegedService.Stub {
    @Keep public PrivilegedService() {}
    @Override public synchronized String run(String command) {
        CommandResult result;
        if (command.startsWith("@sensor-privacy")) {
            try {
                IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String.class).invoke(null, "sensor_privacy");
                Class<?> api = Class.forName("android.hardware.ISensorPrivacyManager");
                Object service = Class.forName("android.hardware.ISensorPrivacyManager$Stub")
                        .getMethod("asInterface", IBinder.class).invoke(null, binder);
                if (command.equals("@sensor-privacy true") || command.equals("@sensor-privacy false")) {
                    api.getMethod("setSensorPrivacy", boolean.class).invoke(service, command.endsWith("true"));
                } else if (!command.equals("@sensor-privacy")) {
                    throw new IllegalArgumentException("Unknown sensor privacy operation");
                }
                result = new CommandResult(0, String.valueOf(api.getMethod("isSensorPrivacyEnabled").invoke(service)));
            } catch (Exception e) {
                result = new CommandResult(-1, "Sensor privacy unavailable: " + e);
            }
        } else {
            result = ProcessRunner.run(new String[]{"sh", "-c", command}, 10000);
        }
        try { return new JSONObject().put("code", result.code).put("output", result.output).toString(); }
        catch (Exception e) { return "{\"code\":-1,\"output\":\"Result encoding failed\"}"; }
    }
    @Override public void destroy() { System.exit(0); }
}
