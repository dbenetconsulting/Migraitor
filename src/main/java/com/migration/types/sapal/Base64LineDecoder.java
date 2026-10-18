package com.migration.types.sapal;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;

/**
 * Decodes the Base64 lines of the SAP RFC response. Works whether each line was encoded on its own
 * (padded with '=') or the lines are slices of one continuous Base64 text, which covers both ways
 * v1 decoded them (per line in main, concatenated in the Viega branch).
 */
final class Base64LineDecoder {

    private final OutputStream out;
    private final StringBuilder pending = new StringBuilder();

    Base64LineDecoder(OutputStream out) {
        this.out = out;
    }

    void line(String raw) throws IOException {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!Character.isWhitespace(c)) pending.append(c);
        }
        if (pending.length() > 0 && pending.charAt(pending.length() - 1) == '=') {
            flushAll();
        } else {
            int usable = pending.length() - (pending.length() % 4);
            if (usable > 0) {
                out.write(Base64.getDecoder().decode(pending.substring(0, usable)));
                pending.delete(0, usable);
            }
        }
    }

    void finish() throws IOException {
        flushAll();
    }

    private void flushAll() throws IOException {
        if (pending.length() == 0) return;
        out.write(Base64.getDecoder().decode(pending.toString()));
        pending.setLength(0);
    }
}
