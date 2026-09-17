package com.akylas.enforcedoze;

import static org.junit.Assert.*;

import org.junit.Test;

public class StateParserTest {
    @Test
    public void maintenanceIsNotIdle() {
        assertEquals(
                "IDLE_MAINTENANCE",
                StateParser.read(
                        "deep",
                        new CommandResult(0, "mLightState=IDLE mState=IDLE_MAINTENANCE\n")));
    }

    @Test
    public void unknownIsNotSuccess() {
        assertNull(
                StateParser.read(
                        "sensor", new CommandResult(0, "Permission Denial: sensorservice")));
        assertNull(StateParser.read("switch", new CommandResult(0, "null")));
        assertNull(StateParser.read("forced", new CommandResult(0, "mState=IDLE")));
    }

    @Test
    public void preservesSensorWhitelistState() {
        assertEquals(
                "RESTRICTED : test.pkg",
                StateParser.read(
                        "sensor",
                        new CommandResult(
                                0, "Mode : RESTRICTED : test.pkg\nSensor Privacy: disabled")));
    }

    @Test
    public void rejectsInjectedPackages() {
        assertFalse(CommandResult.validPackage("app.name;reboot"));
        assertFalse(CommandResult.validPackage("$(id)"));
        assertFalse(CommandResult.validPackage(null));
        assertTrue(CommandResult.validPackage("com.example.app_2"));
        assertEquals("'a'\\''b'", CommandResult.quote("a'b"));
    }
}
