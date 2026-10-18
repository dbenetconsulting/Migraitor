package com.migration.web;

import com.migration.core.MigrationTypes;
import com.migration.entities.ImportRunEntity;
import com.migration.entities.SourceDocumentEntity;
import com.migration.otcs.ConnectionService.Role;
import com.migration.repositories.ImportRunRepository;
import com.migration.repositories.SourceDocumentRepository;
import com.migration.types.cstocs.CsToCsImporter;
import com.migration.types.cstocs.CsToCsType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Import tab: exports from source and target, match, and the report of what did not match. */
@RestController
@RequestMapping("/api/import/{migType}")
public class ImportApi {

    private static final List<String> NOT_MATCHED = List.of("NO_EMPLOYEE", "NO_FOLDER", "AMBIGUOUS_FOLDER", "INVALID");

    private final CsToCsImporter importer;
    private final ImportRunRepository runs;
    private final SourceDocumentRepository sourceDocs;
    private final MigrationTypes types;

    public ImportApi(CsToCsImporter importer, ImportRunRepository runs, SourceDocumentRepository sourceDocs, MigrationTypes types) {
        this.importer = importer;
        this.runs = runs;
        this.sourceDocs = sourceDocs;
        this.types = types;
    }

    @GetMapping("/runs")
    public List<ImportRunEntity> runs(@PathVariable String migType) {
        check(migType);
        return runs.findByMigTypeOrderByRunIdDesc(migType, PageRequest.of(0, 50));
    }

    @PostMapping("/{role}/report")
    public ImportRunEntity fromReport(@PathVariable String migType, @PathVariable Role role, Principal user) {
        check(migType);
        return importer.startLiveReport(role, user.getName());
    }

    @PostMapping(value = "/{role}/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportRunEntity fromCsv(@PathVariable String migType, @PathVariable Role role,
                                   @RequestPart("file") MultipartFile file, Principal user) throws IOException {
        check(migType);
        if (file.isEmpty()) throw new IllegalArgumentException("The file is empty");
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return importer.importCsv(role, reader, user.getName());
        }
    }

    public static class MatchRequest {
        public long sourceRunId;
        public long targetRunId;
    }

    @PostMapping("/match")
    public CsToCsImporter.MatchSummary match(@PathVariable String migType, @RequestBody MatchRequest req) {
        check(migType);
        return importer.match(req.sourceRunId, req.targetRunId);
    }

    @GetMapping("/runs/{runId}/summary")
    public Map<String, Long> summary(@PathVariable String migType, @PathVariable long runId) {
        Map<String, Long> m = new LinkedHashMap<>();
        sourceDocs.countByMatchStatus(runId).forEach(c -> m.put(c.getStatus(), c.getTotal()));
        return m;
    }

    @GetMapping("/runs/{runId}/unmatched")
    public Page<SourceDocumentEntity> unmatched(@PathVariable String migType, @PathVariable long runId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return sourceDocs.findByRunIdAndMatchStatusInOrderById(runId, NOT_MATCHED, PageRequest.of(page, Math.min(size, 500)));
    }

    /** Unmatched documents as CSV (semicolon), to send to the client or fix the target. */
    @GetMapping("/runs/{runId}/unmatched.csv")
    public ResponseEntity<String> unmatchedCsv(@PathVariable String migType, @PathVariable long runId) {
        StringBuilder csv = new StringBuilder("DATAID;EMPLOYEEID;NAME;FOLDERNAME;MATCHSTATUS\n");
        int page = 0;
        Page<SourceDocumentEntity> p;
        do {
            p = sourceDocs.findByRunIdAndMatchStatusInOrderById(runId, NOT_MATCHED, PageRequest.of(page++, 1000));
            for (SourceDocumentEntity d : p) {
                csv.append(d.getDataId()).append(';').append(cell(d.getEmployeeId())).append(';').append(cell(d.getName()))
                        .append(';').append(cell(d.getFolderName())).append(';').append(d.getMatchStatus()).append('\n');
            }
        } while (p.hasNext());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=unmatched_run_" + runId + ".csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString());
    }

    private void check(String migType) {
        if (!types.get(migType).supportsImport() || !CsToCsType.CODE.equals(migType)) {
            throw new IllegalArgumentException("Type " + migType + " has no import step");
        }
    }

    private static String cell(String s) {
        if (s == null) return "";
        return s.contains(";") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
