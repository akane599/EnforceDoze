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
            if (command == null || !command.startsWith(PREFIX)) return new CommandResult(-1, "Invalid operation");
            String[] args = command.substring(PREFIX.length()).split(" ");
            Object result = null;
            if (args[0].equals("hotspot") && args.length == 2 && args[1].equals("get")) {
                Object manager = service("wifi", "android.net.wifi.IWifiManager$Stub");
                result = Class.forName("android.net.wifi.IWifiManager").getMethod("getWifiApEnabledState").invoke(manager);
            } else if (args[0].equals("motion")) {
                return SensorRestriction.run(args, value -> ProcessRunner.run(2000, "/system/bin/sh", "-c", value));
            } else if (args[0].equals("sensors") && args.length == 2) {
                Object manager = service("sensor_privacy", "android.hardware.ISensorPrivacyManager$Stub");
                Class<?> api = Class.forName("android.hardware.ISensorPrivacyManager");
                if (args[1].equals("get")) result = api.getMethod("isSensorPrivacyEnabled").invoke(manager);
                else {
                    boolean requested = strictBoolean(args[1]);
                    boolean before = (Boolean) api.getMethod("isSensorPrivacyEnabled").invoke(manager);
                    api.getMethod("setSensorPrivacy", boolean.class).invoke(manager, requested);
                    boolean after = (Boolean) api.getMethod("isSensorPrivacyEnabled").invoke(manager);
                    return new CommandResult(after == requested ? 0 : -1,
                            "Before: Sensors off=" + before + "\nAfter: Sensors off=" + after
                                    + "\nRequested state verified: " + (after == requested));
                }
            } else if (args[0].equals("notifications") && args.length == 4 && CommandPolicy.validPackage(args[2])) {
                Object manager = service("notification", "android.app.INotificationManager$Stub");
                Class<?> api = Class.forName("android.app.INotificationManager");
                int uid = Integer.parseInt(args[3]);
                if (uid < 0) return new CommandResult(-1, "Invalid package UID");
                if (args[1].equals("get")) result = api.getMethod("areNotificationsEnabledForPackage", String.class, int.class)
                        .invoke(manager, args[2], uid);
                else {
                    boolean requested = strictBoolean(args[1]);
                    api.getMethod("setNotificationsEnabledForPackage", String.class, int.class, boolean.class)
                            .invoke(manager, args[2], uid, requested);
                    boolean actual = (Boolean) api.getMethod("areNotificationsEnabledForPackage", String.class, int.class)
                            .invoke(manager, args[2], uid);
                    return new CommandResult(actual == requested ? 0 : -1, "Notifications enabled=" + actual
                            + "; requested state verified=" + (actual == requested));
                }
            } else return new CommandResult(-1, "Unsupported operation");
            return new CommandResult(0, result == null ? "" : result.toString());
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return new CommandResult(-1, "Device denied or does not support this operation: " + cause);
        }
    }
    private static boolean strictBoolean(String value) {
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Expected true or false");
        return value.equals("true");
    }
    /** Root backend uses the same named methods through app_process. */
    public static void main(String[] args) {
        CommandResult result = args.length == 1 ? run(args[0]) : new CommandResult(-1, "Missing operation");
        System.out.println(result.output);
        System.exit(result.exitCode);
    }
}
