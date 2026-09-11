package com.akylas.enforcedoze;

import java.util.ArrayList;

/** Platform command names are independent of the preference keys kept for migration. */
final class DozeTunablePolicy {
    private DozeTunablePolicy() { }

    static String modernKey(String key) {
        switch (key) {
            case "light_pre_idle_to": return null; // This state was removed from modern Android.
            case "max_temp_app_whitelist_duration": return "max_temp_app_allowlist_duration_ms";
            case "mms_temp_app_whitelist_duration": return "mms_temp_app_allowlist_duration_ms";
            case "sms_temp_app_whitelist_duration": return "sms_temp_app_allowlist_duration_ms";
            case "notification_whitelist_duration": return "notification_allowlist_duration_ms";
            default: return key;
        }
    }

    static boolean validValue(String key, String value) {
        try {
            double number = Double.parseDouble(value);
            boolean factor = key.endsWith("factor");
            boolean accuracy = key.equals("location_accuracy");
            // Android clamps growth factors to at least one. Report invalid input in the editor.
            if (!factor && !accuracy) Long.parseLong(value);
            return Double.isFinite(number) && number >= (factor ? 1 : accuracy ? 0.1 : 0)
                    && number <= (factor ? 100 : 604800000L)
                    && (factor || accuracy || number == Math.floor(number));
        } catch (RuntimeException e) { return false; }
    }

    static ArrayList<String> modernCommands(String values) {
        ArrayList<String> commands = new ArrayList<>();
        for (String entry : values.split(",")) {
            String[] pair = entry.split("=", -1);
            if (pair.length != 2) throw new IllegalArgumentException("Invalid tunable entry");
            String key = modernKey(pair[0]);
            if (key == null) continue;
            if (!key.matches("[a-z_]+") || !validValue(pair[0], pair[1]))
                throw new IllegalArgumentException("Invalid tunable: " + pair[0]);
            commands.add("device_config put device_idle " + key + " " + pair[1]);
        }
        return commands;
    }
}
