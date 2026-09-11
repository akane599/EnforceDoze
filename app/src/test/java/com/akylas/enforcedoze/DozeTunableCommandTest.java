package com.akylas.enforcedoze;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The service re-derives each tunable's key from the command it is about to run. A command without
 * the {@code device_idle} namespace is rejected by the shell and never applies a single tunable.
 */
public class DozeTunableCommandTest {
    @Test public void everyDeviceConfigCommandCarriesNamespaceKeyAndValue() {
        for (String command : commands()) {
            String[] parts = command.split(" ");
            assertEquals(command, 5, parts.length);
            assertEquals(command, "device_config", parts[0]);
            assertEquals(command, "put", parts[1]);
            assertEquals(command, DozeTunableHandler.DEVICE_CONFIG_NAMESPACE, parts[2]);
            assertFalse(command, parts[3].isEmpty());
            assertFalse(command, parts[4].isEmpty());
        }
    }

    @Test public void commandsAndLegacyConstantsDescribeTheSameTunables() {
        java.util.LinkedHashMap<String, String> tunables = handler().getTunables();
        assertFalse(tunables.isEmpty());
        assertEquals(tunables.size(), commands().size());

        String legacy = handler().getTunableString();
        for (java.util.Map.Entry<String, String> entry : tunables.entrySet()) {
            assertTrue(entry.getKey(), legacy.contains(entry.getKey() + "=" + entry.getValue()));
        }
        // Comma separated, so no trailing separator can create an empty final assignment.
        assertFalse(legacy, legacy.endsWith(","));
        assertEquals(tunables.size() - 1, legacy.chars().filter(c -> c == ',').count());
    }

    @Test public void defaultsAreWithinTheRangeTheTunableEditorAccepts() {
        for (java.util.Map.Entry<String, String> entry : handler().getTunables().entrySet()) {
            double value = Double.parseDouble(entry.getValue());
            assertTrue(entry.getKey(), value >= 0 && value <= 604800000L);
        }
    }

    private DozeTunableHandler handler() {
        // getInstance() reloads from preferences, which needs a live app context.
        return new DozeTunableHandler();
    }

    private java.util.List<String> commands() { return handler().getCommandsList(); }
}
