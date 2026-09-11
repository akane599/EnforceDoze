package com.akylas.enforcedoze;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class SensorRestrictionTest {
    @Test public void parsesExactModeAndRejectsAmbiguousOrMissingState() {
        assertEquals("NORMAL", SensorRestriction.parse("Header\n Mode : NORMAL\nOther details").mode);
        assertEquals("com.example.player", SensorRestriction.parse("Mode : RESTRICTED : com.example.player").exemption);
        assertEquals("UNKNOWN", SensorRestriction.parse("Permission Denial: SensorService").mode);
        assertEquals("UNKNOWN", SensorRestriction.parse("Mode : NORMAL\nMode : RESTRICTED : app").mode);
    }
    @Test public void noExceptionUsesNonemptyUnmatchableArgumentAndVerifiesResult() {
        List<String> commands = new ArrayList<>();
        CommandResult result = SensorRestriction.run(new String[]{"motion", "restrict"}, command -> {
            commands.add(command);
            return new CommandResult(0, commands.size() == 1 ? "Mode : NORMAL"
                    : "Mode : RESTRICTED : " + SensorRestriction.NO_EXEMPTION);
        });
        assertTrue(result.output, result.success());
        assertEquals(Arrays.asList("dumpsys sensorservice", "dumpsys sensorservice restrict '" + SensorRestriction.NO_EXEMPTION + "'"), commands);
        assertFalse(CommandPolicy.validPackage(SensorRestriction.NO_EXEMPTION));
    }
    @Test public void zeroExitWithoutStateChangeIsFailure() {
        CommandResult result = SensorRestriction.run(new String[]{"motion", "restrict"}, command -> new CommandResult(0, "Mode : NORMAL"));
        assertFalse(result.success());
        assertTrue(result.output.contains("After: Mode : NORMAL"));
    }
    @Test public void missingMutationOutputTriggersIndependentReadback() {
        List<String> calls = new ArrayList<>();
        CommandResult result = SensorRestriction.run(new String[]{"motion", "restrict", "com.example.player"}, command -> {
            calls.add(command);
            return new CommandResult(0, calls.size() == 1 ? "Mode : NORMAL" : calls.size() == 2 ? ""
                    : "Mode : RESTRICTED : com.example.player");
        });
        assertTrue(result.output, result.success());
        assertEquals(3, calls.size());
        assertEquals("dumpsys sensorservice", calls.get(2));
    }
    @Test public void restorationDoesNotOverwriteAnotherToolsRestriction() {
        List<String> calls = new ArrayList<>();
        CommandResult result = SensorRestriction.run(new String[]{"motion", "enable", SensorRestriction.NO_EXEMPTION}, command -> {
            calls.add(command); return new CommandResult(0, "Mode : RESTRICTED : com.other.tool");
        });
        assertFalse(result.success());
        assertEquals(1, calls.size());
    }
    @Test public void unreadableInitialStateNeverMutates() {
        List<String> calls = new ArrayList<>();
        assertFalse(SensorRestriction.run(new String[]{"motion", "restrict"}, command -> {
            calls.add(command); return new CommandResult(0, "No Sensors on the device");
        }).success());
        assertEquals(1, calls.size());
    }
}
