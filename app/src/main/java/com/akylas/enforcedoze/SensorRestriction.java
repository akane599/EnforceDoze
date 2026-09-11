package com.akylas.enforcedoze;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SensorService operating mode, not a claim that sensor hardware is powered off. */
final class SensorRestriction {
    // SensorService matches allowlists by substring. An empty argument matches EVERY package.
    // These characters cannot occur in an Android package name, so this matches no app.
    static final String NO_EXEMPTION = "@enforcedoze:no-sensor-clients@";
    private static final Pattern MODE = Pattern.compile("(?m)^[ \\t]*Mode[ \\t]*:[ \\t]*([A-Z_]+)(?:[ \\t]*:[ \\t]*([^\\r\\n]*))?[ \\t]*$");
    interface Runner { CommandResult run(String command); }
    static final class State {
        final String mode, exemption;
        State(String mode, String exemption) { this.mode = mode; this.exemption = exemption; }
        String line() { return "Mode : " + mode + (exemption.isEmpty() ? "" : " : " + exemption); }
        boolean isRestrictedTo(String value) { return mode.equals("RESTRICTED") && exemption.equals(value); }
    }
    static State parse(String output) {
        Matcher match = MODE.matcher(output == null ? "" : output);
        if (!match.find()) return new State("UNKNOWN", "");
        State state = new State(match.group(1), match.group(2) == null ? "" : match.group(2).trim());
        return match.find() ? new State("UNKNOWN", "") : state;
    }
    static String exemption(String requested) {
        if (requested == null || requested.isEmpty() || requested.equals(NO_EXEMPTION)) return NO_EXEMPTION;
        if (!CommandPolicy.validPackage(requested)) throw new IllegalArgumentException("Invalid sensor exception package");
        return requested;
    }
    static CommandResult run(String[] args, Runner runner) {
        if (args.length < 2 || args.length > 3) return new CommandResult(-1, "Invalid motion sensor operation");
        String operation = args[1];
        if (!(operation.equals("get") || operation.equals("restrict") || operation.equals("enable"))
                || operation.equals("get") && args.length != 2) return new CommandResult(-1, "Invalid motion sensor operation");
        String exception = args.length == 3 ? exemption(args[2]) : NO_EXEMPTION;
        CommandResult read = runner.run("dumpsys sensorservice");
        State before = parse(read.output);
        if (!read.success() || before.mode.equals("UNKNOWN"))
            return new CommandResult(-1, "SensorService state unavailable; no change made.\n" + before.line());
        if (operation.equals("get")) return new CommandResult(0, before.line());
        if (operation.equals("restrict") && !before.mode.equals("NORMAL"))
            return new CommandResult(-1, "SensorService is already controlled by another restriction; no change made.\nBefore: " + before.line());
        if (operation.equals("enable")) {
            if (before.mode.equals("NORMAL")) return new CommandResult(0, "Already restored.\nAfter: " + before.line());
            if (!before.mode.equals("RESTRICTED") || args.length == 3 && !before.isRestrictedTo(exception))
                return new CommandResult(-1, "SensorService no longer matches this session; leaving it unchanged.\nBefore: " + before.line());
        }
        String command = "dumpsys sensorservice " + (operation.equals("restrict")
                ? "restrict " + CommandPolicy.quote(exception) : "enable");
        CommandResult changed = runner.run(command);
        // AOSP dumps the resulting state after a successful transition. Some vendors do not.
        State after = parse(changed.output);
        if (after.mode.equals("UNKNOWN")) {
            CommandResult verify = runner.run("dumpsys sensorservice");
            after = verify.success() ? parse(verify.output) : new State("UNKNOWN", "");
        }
        boolean verified = changed.success() && (operation.equals("restrict")
                ? after.isRestrictedTo(exception) : after.mode.equals("NORMAL"));
        return new CommandResult(verified ? 0 : -1,
                (verified ? "Verified SensorService state." : "SensorService did not confirm the requested state; restoration remains required.")
                        + "\nBefore: " + before.line() + "\nAfter: " + after.line() + "\nCommand exit: " + changed.exitCode);
    }
}
