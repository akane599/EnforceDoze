package com.akylas.enforcedoze;

import static org.junit.Assert.*;

import org.junit.Test;

public class TunableRulesTest {
    @Test
    public void timeoutMustBeAnAndroidLongNotAnIntegralDecimal() {
        assertTrue(TunableRules.valid("idle_to", "0"));
        assertTrue(TunableRules.valid("idle_to", "604800000"));
        for (String value :
                new String[] {"1.0", "-1", "604800001", "NaN", "Infinity", "1e3", "1;reboot", ""})
            assertFalse(value, TunableRules.valid("idle_to", value));
    }

    @Test
    public void factorAndAccuracyHaveIndependentBounds() {
        assertTrue(TunableRules.valid("idle_factor", "1.5"));
        assertFalse(TunableRules.valid("idle_factor", "0.5"));
        assertFalse(TunableRules.valid("idle_factor", "10.1"));
        assertTrue(TunableRules.valid("location_accuracy", "0.5"));
        assertFalse(TunableRules.valid("location_accuracy", "10001"));
    }
}
