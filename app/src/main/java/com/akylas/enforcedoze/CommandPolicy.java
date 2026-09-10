package com.akylas.enforcedoze;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandPolicy {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+");
    private static final Pattern STATE = Pattern.compile("(?:^|\\s)mState=([A-Z_]+)(?:\\s|$)");
    public static boolean validPackage(String name) { return name != null && (name.equals("android") || PACKAGE.matcher(name).matches()); }
    public static boolean protectedPackage(String name) {
        return java.util.Arrays.asList("android", "com.akylas.enforcedoze", "moe.shizuku.privileged.api", "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.server.telecom").contains(name);
    }
    public static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    public static String idleState(String output) {
        Matcher m = STATE.matcher(output == null ? "" : output);
        return m.find() ? m.group(1) : "UNKNOWN";
    }
    public static int[] period(String text) {
        if (text == null || !text.matches("\\d{2}:\\d{2}-\\d{2}:\\d{2}")) return null;
        int sh = Integer.parseInt(text.substring(0, 2)), sm = Integer.parseInt(text.substring(3, 5));
        int eh = Integer.parseInt(text.substring(6, 8)), em = Integer.parseInt(text.substring(9, 11));
        if (sh > 23 || eh > 23 || sm > 59 || em > 59 || (sh == eh && sm == em)) return null;
        return new int[]{sh * 60 + sm, eh * 60 + em};
    }
    public static boolean contains(int minute, int[] period) {
        return period != null && (period[0] < period[1]
                ? minute >= period[0] && minute < period[1] : minute >= period[0] || minute < period[1]);
    }
}
