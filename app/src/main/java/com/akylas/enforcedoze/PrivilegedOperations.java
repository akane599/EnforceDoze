package com.akylas.enforcedoze;

import android.os.IBinder;
import java.lang.reflect.Method;

/** Named Binder methods resolved in the privileged process, never hardcoded transaction IDs. */
public final class PrivilegedOperations {
    public static final String PREFIX = "enforcedoze-internal ";
    private static Object service(String name, String stub) throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, name);
        if (binder == null) throw new IllegalStateException("Service unavailable: " + name);
        return Class.forName(stub).getMethod("asInterface", IBinder.class).invoke(null, binder);
    }
    public static CommandResult run(String command) {
        try {
            String[] args = command.substring(PREFIX.length()).split(" ");
            Object result = null;
            if (args[0].equals("hotspot") && args.length == 2 && args[1].equals("get")) {
                Object manager = service("wifi", "android.net.wifi.IWifiManager$Stub");
                result = Class.forName("android.net.wifi.IWifiManager").getMethod("getWifiApEnabledState").invoke(manager);
            } else if (args[0].equals("sensors") && args.length == 2) {
                Object manager = service("sensor_privacy", "android.hardware.ISensorPrivacyManager$Stub");
                Class<?> api = Class.forName("android.hardware.ISensorPrivacyManager");
                if (args[1].equals("get")) result = api.getMethod("isSensorPrivacyEnabled").invoke(manager);
                else api.getMethod("setSensorPrivacy", boolean.class).invoke(manager, Boolean.parseBoolean(args[1]));
            } else if (args[0].equals("notifications") && args.length == 4 && CommandPolicy.validPackage(args[2])) {
                Object manager = service("notification", "android.app.INotificationManager$Stub");
                Class<?> api = Class.forName("android.app.INotificationManager");
                int uid = Integer.parseInt(args[3]);
                if (args[1].equals("get")) result = api.getMethod("areNotificationsEnabledForPackage", String.class, int.class)
                        .invoke(manager, args[2], uid);
                else api.getMethod("setNotificationsEnabledForPackage", String.class, int.class, boolean.class)
                        .invoke(manager, args[2], uid, Boolean.parseBoolean(args[1]));
            } else return new CommandResult(-1, "Unsupported operation");
            return new CommandResult(0, result == null ? "" : result.toString());
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return new CommandResult(-1, "Device denied or does not support this operation: " + cause);
        }
    }
    /** Root backend uses the same named methods through app_process. */
    public static void main(String[] args) {
        CommandResult result = args.length == 1 ? run(args[0]) : new CommandResult(-1, "Missing operation");
        System.out.println(result.output);
        System.exit(result.exitCode);
    }
}
