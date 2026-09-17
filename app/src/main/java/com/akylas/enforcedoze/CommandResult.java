package com.akylas.enforcedoze;

/** An exit status is transport evidence; callers must still observe the requested state. */
public final class CommandResult {
    public final int code;
    public final String output;
    public CommandResult(int code, String output) {
        this.code = code;
        this.output = output == null ? "" : output;
    }
    public boolean ok() {
        return code == 0 && !output.contains("Permission Denial")
                && !output.contains("SecurityException") && !output.contains("Error:");
    }
    public static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
    public static boolean validPackage(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }
}
