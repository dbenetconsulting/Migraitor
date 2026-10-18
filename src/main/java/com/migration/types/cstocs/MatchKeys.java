package com.migration.types.cstocs;

import java.text.Normalizer;
import java.util.Locale;

/** Normalization used to compare employee ids and folder names between source and target. */
final class MatchKeys {

    private MatchKeys() {}

    /** Trims; for numeric ids also drops leading zeros (00001234 and 1234 are the same employee). */
    static String employee(String id) {
        if (id == null) return null;
        String s = id.trim();
        if (s.isEmpty()) return null;
        if (s.matches("\\d+")) {
            s = s.replaceFirst("^0+(?=\\d)", "");
        }
        return s.toUpperCase(Locale.ROOT);
    }

    /** Case-insensitive, accents kept, Unicode-normalized, repeated spaces collapsed. */
    static String folder(String name) {
        if (name == null) return null;
        String s = Normalizer.normalize(name, Normalizer.Form.NFC).replace(' ', ' ').trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s.toLowerCase(Locale.ROOT);
    }

    static String key(String employeeId, String folderName) {
        return employee(employeeId) + "|" + folder(folderName);
    }
}
