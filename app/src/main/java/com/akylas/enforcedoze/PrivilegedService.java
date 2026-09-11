package com.akylas.enforcedoze;

public final class PrivilegedService extends IPrivilegedService.Stub {
    public PrivilegedService() {}
    @Override public synchronized String[] execute(String command) {
        if (command == null || command.isEmpty() || command.length() > 32768) return new CommandResult(-1, "Invalid command").encode();
        return (command.startsWith(PrivilegedOperations.PREFIX) ? PrivilegedOperations.run(command)
                : ProcessRunner.run(8000, "/system/bin/sh", "-c", command)).encode();
    }
    @Override public void destroy() { System.exit(0); }
}
