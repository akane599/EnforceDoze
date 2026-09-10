package com.akylas.enforcedoze;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Bounded, merged process output. Never mistake an empty output for success. */
public final class CommandResult {
    public final int exitCode;
    public final String output;
    public CommandResult(int exitCode, String output) {
        this.exitCode = exitCode;
        this.output = output == null ? "" : output;
    }
    public boolean success() { return exitCode == 0; }
    public List<String> lines() {
        return output.isEmpty() ? Collections.emptyList() : Arrays.asList(output.split("\\r?\\n"));
    }
    public String[] encode() { return new String[]{String.valueOf(exitCode), output}; }
    public static CommandResult decode(String[] value) {
        if (value == null || value.length != 2) return new CommandResult(-1, "Invalid service response");
        try { return new CommandResult(Integer.parseInt(value[0]), value[1]); }
        catch (NumberFormatException e) { return new CommandResult(-1, "Invalid exit code"); }
    }
}
