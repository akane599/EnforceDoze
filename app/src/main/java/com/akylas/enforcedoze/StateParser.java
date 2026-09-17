package com.akylas.enforcedoze;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StateParser {
    private StateParser() {}
    private static String match(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
    public static String read(String kind, CommandResult result) {
        if (!result.ok()) return null;
        String text = result.output.trim();
        switch (kind) {
            case "deep": return match(text, "(?:^|\\s)mState=([A-Z_]+)(?:\\s|$)");
            case "forced": return match(text, "(?:^|\\s)mForceIdle=(true|false)(?:\\s|$)");
            case "sensor": return match(text, "^\\s*Mode\\s*:\\s*([^\\r\\n]+)");
            case "boolean": return text.equals("true") || text.equals("false") ? text : null;
            case "switch": return text.equals("0") || text.equals("1") ? text : null;
            case "setting": return text.isEmpty() ? null : text;
            case "wifi":
                if (text.contains("Wifi is enabled")) return "1";
                if (text.contains("Wifi is disabled")) return "0";
                return null;
            default: return null;
        }
    }
}
