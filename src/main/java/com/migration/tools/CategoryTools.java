package com.migration.tools;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Category helpers of v1 (CategoryMigrationRunner), now working on uploaded text instead of fixed
 * paths on S:\. Same input and output formats, so existing mapping files keep working.
 * <ul>
 *   <li>parse: raw LiveReport export "DataID;{catId,ver}...'ID'=2,'Values'={'x'}..." to
 *       {@code DataID; "cat_id": "catId"; "catId_2": "x";}</li>
 *   <li>map: replaces category and attribute keys with a mapping file "OldValue;NewValue".</li>
 * </ul>
 */
@Service
public class CategoryTools {

    private static final Pattern CATEGORY = Pattern.compile("\\{(\\d+),\\d+\\}");
    private static final Pattern FIELD = Pattern.compile("'ID'=(\\d+),'Values'=\\{\\{?'([^'}]+)'\\}?\\}");

    public String parse(String raw) {
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\\R")) {
            if (line.trim().isEmpty()) continue;
            out.append(parseLine(line)).append('\n');
        }
        return out.toString();
    }

    static String parseLine(String input) {
        StringBuilder sb = new StringBuilder();
        sb.append(input.split(";", 2)[0].trim()).append("; ");
        Matcher cat = CATEGORY.matcher(input);
        if (cat.find()) {
            String catId = cat.group(1);
            sb.append("\"cat_id\": \"").append(catId).append("\"; ");
            Matcher field = FIELD.matcher(input);
            while (field.find()) {
                sb.append('"').append(catId).append('_').append(field.group(1)).append("\": \"").append(field.group(2)).append("\"; ");
            }
        }
        return sb.toString().trim();
    }

    public String map(String mappingCsv, String input) {
        Map<String, String> categories = new LinkedHashMap<>();
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : mappingCsv.split("\\R")) {
            line = line.replace("\uFEFF", "").trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("OldValue")) continue;
            String[] parts = line.split(";", -1);
            if (parts.length < 2) continue;
            (parts[0].trim().startsWith("\"cat_id\"") ? categories : fields).put(parts[0].trim(), parts[1].trim());
        }

        StringBuilder out = new StringBuilder("DataId;Metadata\n");
        String[] lines = input.split("\\R");
        for (int n = 1; n < lines.length; n++) {          // first line is the header, as in v1
            String[] parts = lines[n].split(";");
            if (parts.length < 2) continue;
            String catId = parts[1].trim();
            out.append(parts[0].trim()).append(';').append(categories.getOrDefault(catId, catId));
            for (int i = 2; i < parts.length; i++) {
                String[] kv = parts[i].split(":", 2);
                if (kv.length != 2) continue;
                String key = kv[0].trim();
                out.append(';').append(fields.getOrDefault(key, key)).append(':').append(kv[1].trim());
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** Lines of a parse result, for previews. */
    public static List<String> head(String text, int max) {
        List<String> lines = new ArrayList<>();
        for (String l : text.split("\\R")) {
            if (lines.size() >= max) break;
            lines.add(l);
        }
        return lines;
    }
}
