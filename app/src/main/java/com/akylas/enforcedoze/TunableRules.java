package com.akylas.enforcedoze;

/** Accept the actual integer/float syntax consumed by Android's constants parser. */
final class TunableRules {
    static boolean valid(String key, String value) {
        if (key == null || value == null || !value.matches("[0-9]+(?:\\.[0-9]+)?")) return false;
        try {
            double number = Double.parseDouble(value);
            if (Double.isNaN(number) || Double.isInfinite(number)) return false;
            if (key.endsWith("factor")) return number >= 1 && number <= 10;
            if (key.equals("location_accuracy")) return number >= 0 && number <= 10000;
            return value.matches("[0-9]+") && number >= 0 && number <= 604800000;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
