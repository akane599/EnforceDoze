package com.akylas.enforcedoze;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProcessRunnerTest {
    @Test public void drainsLargeStderrWithoutPipeDeadlock() {
        CommandResult result = ProcessRunner.run(4000, "sh", "-c", "i=0; while [ $i -lt 10000 ]; do echo errorline >&2; i=$((i+1)); done; echo done");
        assertEquals(0, result.exitCode); assertTrue(result.output.contains("done"));
    }
    @Test public void exitCodeSurvivesEmptyOutput() { assertEquals(7, ProcessRunner.run(2000, "sh", "-c", "exit 7").exitCode); }
    @Test public void timesOutAndBoundsOutput() {
        long start = System.nanoTime();
        CommandResult result = ProcessRunner.run(100, "sh", "-c", "while :; do echo output; done");
        assertEquals(124, result.exitCode);
        assertTrue(result.output.length() <= 131200);
        assertTrue((System.nanoTime() - start) / 1000000 < 3000);
    }
}
