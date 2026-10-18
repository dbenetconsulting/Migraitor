package com.migration.types.cstocs;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Turns a LiveReport JSON page or a CSV export into rows keyed by upper-case column name. */
final class ImportRows {

    private ImportRows() {}

    /** Finds the row array in a report output ({"data":[...]}, {"results":{"data":[...]}}, or a bare array). */
    static List<Map<String, String>> fromJson(JsonNode root) {
        JsonNode array = findRowArray(root);
        List<Map<String, String>> rows = new ArrayList<>();
        if (array == null) return rows;
        for (JsonNode item : array) {
            JsonNode obj = item.has("data") && item.get("data").isObject() ? item.get("data") : item;
            if (!obj.isObject()) continue;
            Map<String, String> row = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> f = it.next();
                JsonNode v = f.getValue();
                row.put(f.getKey().toUpperCase(Locale.ROOT), v == null || v.isNull() ? null : v.isValueNode() ? v.asText() : v.toString());
            }
            rows.add(row);
        }
        return rows;
    }

    private static JsonNode findRowArray(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) return node;
        for (String key : new String[]{"data", "results", "myRows", "rows"}) {
            JsonNode child = node.get(key);
            if (child == null) continue;
            if (child.isArray()) return child;
            JsonNode deeper = findRowArray(child);
            if (deeper != null) return deeper;
        }
        return null;
    }

    /**
     * Reads a CSV with a header row. The delimiter (; , or tab) is taken from the header line,
     * quoted values may contain delimiters, quotes ("") and line breaks.
     */
    static int fromCsv(Reader input, Consumer<Map<String, String>> sink) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        reader.mark(1 << 16);
        String headerLine = reader.readLine();
        if (headerLine == null) return 0;
        reader.reset();
        char delimiter = detectDelimiter(headerLine);

        List<String> header = null;
        int count = 0;
        List<String> record;
        while ((record = nextRecord(reader, delimiter)) != null) {
            if (header == null) {
                header = new ArrayList<>();
                for (String h : record) header.add(h.replace("﻿", "").trim().toUpperCase(Locale.ROOT));
                continue;
            }
            if (record.size() == 1 && record.get(0).isBlank()) continue;
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) row.put(header.get(i), i < record.size() ? record.get(i) : null);
            sink.accept(row);
            count++;
        }
        return count;
    }

    private static char detectDelimiter(String header) {
        char best = ',';
        long bestCount = -1;
        for (char c : new char[]{';', ',', '\t'}) {
            long n = header.chars().filter(ch -> ch == c).count();
            if (n > bestCount) { best = c; bestCount = n; }
        }
        return best;
    }

    private static List<String> nextRecord(BufferedReader reader, char delimiter) throws IOException {
        int c = reader.read();
        if (c == -1) return null;
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        while (c != -1) {
            char ch = (char) c;
            if (quoted) {
                if (ch == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else { quoted = false; if (next != -1) reader.reset(); }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"' && field.length() == 0) {
                quoted = true;
            } else if (ch == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                break;
            } else if (ch != '\r') {
                field.append(ch);
            }
            c = reader.read();
        }
        fields.add(field.toString());
        return fields;
    }
}
