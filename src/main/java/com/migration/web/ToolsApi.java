package com.migration.web;

import com.migration.tools.CategoryTools;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/tools/categories")
public class ToolsApi {

    private final CategoryTools tools;

    public ToolsApi(CategoryTools tools) {
        this.tools = tools;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> parse(@RequestPart("file") MultipartFile file) throws IOException {
        return download("categories_parsed.txt", tools.parse(text(file)));
    }

    @PostMapping(value = "/map", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> map(@RequestPart("mapping") MultipartFile mapping, @RequestPart("file") MultipartFile file) throws IOException {
        return download("categories_mapped.csv", tools.map(text(mapping), text(file)));
    }

    private static String text(MultipartFile f) throws IOException {
        if (f.isEmpty()) throw new IllegalArgumentException("File " + f.getOriginalFilename() + " is empty");
        return new String(f.getBytes(), StandardCharsets.UTF_8);
    }

    private static ResponseEntity<String> download(String name, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name)
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(body);
    }
}
