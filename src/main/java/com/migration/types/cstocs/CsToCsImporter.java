package com.migration.types.cstocs;

import com.fasterxml.jackson.databind.JsonNode;
import com.migration.core.DocStatus;
import com.migration.entities.CsMetadataEntity;
import com.migration.entities.ImportRunEntity;
import com.migration.entities.MigrationEntity;
import com.migration.entities.SourceDocumentEntity;
import com.migration.entities.TargetFolderEntity;
import com.migration.otcs.ConnectionService;
import com.migration.otcs.ConnectionService.Role;
import com.migration.otcs.OtcsClient;
import com.migration.otcs.OtcsEndpoint;
import com.migration.repositories.CsMetadataRepository;
import com.migration.repositories.ImportRunRepository;
import com.migration.repositories.MigrationRepository;
import com.migration.repositories.SourceDocumentRepository;
import com.migration.repositories.TargetFolderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Reader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Deichmann staging. Replaces the manual step of v1 (download both LiveReport results, cross them
 * in Excel, load the result into migration/cs_metadata):
 * <ol>
 *   <li>Export SOURCE: documents to migrate (LiveReport on the source CS, or CSV) into src_document.</li>
 *   <li>Export TARGET: employee folders under "999 Altakte" (LiveReport on the target CS, or CSV) into tgt_folder.</li>
 *   <li>Match: employee id + folder name. Matched rows become PENDING rows in migration + cs_metadata.</li>
 * </ol>
 * Each step is a run (import_run) so it can be repeated and audited. Nothing is sent to Content Server.
 */
@Service
public class CsToCsImporter {

    private static final Logger log = LoggerFactory.getLogger(CsToCsImporter.class);
    private static final int CHUNK = 500;

    private final OtcsClient otcs;
    private final ConnectionService connections;
    private final CsToCsProperties.Import cfg;
    private final ImportRunRepository runs;
    private final SourceDocumentRepository sourceDocs;
    private final TargetFolderRepository targetFolders;
    private final MigrationRepository migrations;
    private final CsMetadataRepository csMetadata;
    private final TransactionTemplate tx;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "import");
        t.setDaemon(true);
        return t;
    });

    public CsToCsImporter(OtcsClient otcs, ConnectionService connections, CsToCsProperties props, ImportRunRepository runs,
                          SourceDocumentRepository sourceDocs, TargetFolderRepository targetFolders,
                          MigrationRepository migrations, CsMetadataRepository csMetadata, TransactionTemplate tx) {
        this.otcs = otcs;
        this.connections = connections;
        this.cfg = props.getImporter();
        this.runs = runs;
        this.sourceDocs = sourceDocs;
        this.targetFolders = targetFolders;
        this.migrations = migrations;
        this.csMetadata = csMetadata;
        this.tx = tx;
    }

    // ------------------------------------------------------------------ exports

    /** Starts reading the LiveReport configured on the connection; returns the run at once. */
    public ImportRunEntity startLiveReport(Role role, String user) {
        OtcsEndpoint ep = connections.endpoint(CsToCsType.CODE, role);
        long reportId = connections.liveReportId(CsToCsType.CODE, role)
                .orElseThrow(() -> new IllegalArgumentException("No WebReport id configured on the " + role + " connection"));
        ImportRunEntity run = newRun(role, "LIVEREPORT", user);
        executor.submit(() -> execute(run, () -> readReport(run, ep, reportId)));
        return run;
    }

    /** Loads a CSV export (same columns as the LiveReport) synchronously. */
    public ImportRunEntity importCsv(Role role, Reader csv, String user) {
        ImportRunEntity run = newRun(role, "CSV", user);
        execute(run, () -> {
            List<Map<String, String>> buffer = new ArrayList<>();
            int[] total = {0};
            ImportRows.fromCsv(csv, row -> {
                buffer.add(row);
                if (buffer.size() >= CHUNK) {
                    total[0] += store(run, buffer);
                    buffer.clear();
                }
            });
            total[0] += store(run, buffer);
            return total[0];
        });
        return runs.findById(run.getRunId()).orElse(run);
    }

    private int readReport(ImportRunEntity run, OtcsEndpoint ep, long reportId) {
        String idColumn = run.getRole().equals(Role.SOURCE.name()) ? cfg.getColDataId() : cfg.getColTargetFolderId();
        long cursor = 0;
        int total = 0;
        for (int page = 0; page < cfg.getMaxPages(); page++) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put(cfg.getCursorParam(), String.valueOf(cursor));
            params.put(cfg.getPageSizeParam(), String.valueOf(cfg.getPageSize()));
            JsonNode json = otcs.runReport(ep, cfg.getReportPath(), String.valueOf(reportId), params);
            List<Map<String, String>> rows = ImportRows.fromJson(json);
            if (rows.isEmpty()) break;

            long maxId = cursor;
            for (Map<String, String> r : rows) {
                Long id = parseLong(r.get(idColumn.toUpperCase()));
                if (id != null && id > maxId) maxId = id;
            }
            total += store(run, rows);
            updateCount(run, total);
            if (maxId <= cursor) {
                throw new IllegalStateException("The LiveReport does not page by " + idColumn
                        + " (the page did not move past " + cursor + "). Check the LiveReport filter 'DataID > %1' and ORDER BY DataID.");
            }
            cursor = maxId;
            if (rows.size() < cfg.getPageSize()) break;
        }
        return total;
    }

    private int store(ImportRunEntity run, List<Map<String, String>> rows) {
        if (rows.isEmpty()) return 0;
        boolean source = run.getRole().equals(Role.SOURCE.name());
        return tx.execute(s -> {
            if (source) {
                List<SourceDocumentEntity> docs = rows.stream().map(r -> toSource(run.getRunId(), r)).collect(Collectors.toList());
                sourceDocs.saveAll(docs);
                return docs.size();
            }
            List<TargetFolderEntity> folders = rows.stream().map(r -> toTarget(run.getRunId(), r)).collect(Collectors.toList());
            targetFolders.saveAll(folders);
            return folders.size();
        });
    }

    private SourceDocumentEntity toSource(Long runId, Map<String, String> r) {
        SourceDocumentEntity d = new SourceDocumentEntity();
        d.setRunId(runId);
        d.setDataId(parseLong(col(r, cfg.getColDataId())));
        d.setEmployeeId(trim(col(r, cfg.getColEmployeeId())));
        d.setName(trim(col(r, cfg.getColName())));
        d.setDocType(trim(col(r, cfg.getColDocType())));
        d.setSizeBytes(parseLong(col(r, cfg.getColSize())));
        d.setMimeType(trim(col(r, cfg.getColMimeType())));
        d.setCreateDate(trim(col(r, cfg.getColCreateDate())));
        d.setFolderName(trim(col(r, cfg.getColFolderName())));
        d.setParentId(parseLong(col(r, cfg.getColParentId())));
        d.setTerminationDate(trim(col(r, cfg.getColTerminationDate())));
        return d;
    }

    private TargetFolderEntity toTarget(Long runId, Map<String, String> r) {
        TargetFolderEntity f = new TargetFolderEntity();
        f.setRunId(runId);
        f.setEmployeeId(trim(col(r, cfg.getColTargetEmployeeId())));
        f.setEmployeeName(trim(col(r, cfg.getColTargetEmployeeName())));
        f.setFolderId(parseLong(col(r, cfg.getColTargetFolderId())));
        f.setFolderName(trim(col(r, cfg.getColTargetFolderName())));
        f.setParentName(trim(col(r, cfg.getColTargetParentName())));
        return f;
    }

    // ------------------------------------------------------------------ match

    /** Result of a match, by matchStatus. */
    public static final class MatchSummary {
        public final long sourceRunId;
        public final long targetRunId;
        public final Map<String, Integer> counts = new LinkedHashMap<>();

        MatchSummary(long sourceRunId, long targetRunId) {
            this.sourceRunId = sourceRunId;
            this.targetRunId = targetRunId;
        }

        void add(String status) { counts.merge(status, 1, Integer::sum); }
    }

    /**
     * Crosses a source run with a target run. Only rows still NEW are processed, so running it again
     * after fixing the target (e.g. creating missing folders) picks up what was left. Documents
     * already in cs_metadata (same source DataID) are never staged twice.
     */
    public MatchSummary match(long sourceRunId, long targetRunId) {
        ImportRunEntity sourceRun = runs.findById(sourceRunId).orElseThrow(() -> new IllegalArgumentException("Unknown source run " + sourceRunId));
        ImportRunEntity targetRun = runs.findById(targetRunId).orElseThrow(() -> new IllegalArgumentException("Unknown target run " + targetRunId));
        if (!Role.SOURCE.name().equals(sourceRun.getRole()) || !Role.TARGET.name().equals(targetRun.getRole())) {
            throw new IllegalArgumentException("Pick one SOURCE run and one TARGET run");
        }

        String parent = MatchKeys.folder(cfg.getTargetParentFolder());
        Set<String> employees = new HashSet<>();
        Map<String, List<TargetFolderEntity>> folders = new HashMap<>();
        for (TargetFolderEntity f : targetFolders.findByRunId(targetRunId)) {
            String emp = MatchKeys.employee(f.getEmployeeId());
            if (emp == null || f.getFolderId() == null) continue;
            employees.add(emp);
            // When the export says where the folder hangs, only folders under "999 Altakte" count.
            if (parent != null && f.getParentName() != null && !parent.equals(MatchKeys.folder(f.getParentName()))) continue;
            folders.computeIfAbsent(MatchKeys.key(f.getEmployeeId(), f.getFolderName()), k -> new ArrayList<>()).add(f);
        }

        MatchSummary summary = new MatchSummary(sourceRunId, targetRunId);
        int page = 0;
        while (true) {
            // Always page 0: processed rows leave the NEW filter.
            Page<SourceDocumentEntity> batch = sourceDocs.findByRunIdAndMatchStatusInOrderById(sourceRunId, List.of("NEW"), PageRequest.of(0, CHUNK));
            if (batch.isEmpty()) break;
            tx.executeWithoutResult(s -> matchBatch(batch.getContent(), employees, folders, summary));
            if (++page > 100_000) break;
        }
        sourceRun.setStatus("MATCHED");
        sourceRun.setMessage("Matched with target run " + targetRunId + ": " + summary.counts);
        runs.save(sourceRun);
        log.info("Match source run {} / target run {}: {}", sourceRunId, targetRunId, summary.counts);
        return summary;
    }

    private void matchBatch(List<SourceDocumentEntity> batch, Set<String> employees,
                            Map<String, List<TargetFolderEntity>> folders, MatchSummary summary) {
        List<Integer> ids = batch.stream().map(SourceDocumentEntity::getDataId).filter(id -> id != null && id <= Integer.MAX_VALUE)
                .map(Long::intValue).collect(Collectors.toList());
        Set<Integer> staged = ids.isEmpty() ? Set.of() : new HashSet<>(csMetadata.findExistingDocIds(ids));

        for (SourceDocumentEntity d : batch) {
            String status;
            if (d.getDataId() == null || d.getDataId() > Integer.MAX_VALUE || MatchKeys.employee(d.getEmployeeId()) == null
                    || d.getName() == null || MatchKeys.folder(d.getFolderName()) == null) {
                status = "INVALID";
            } else if (staged.contains(d.getDataId().intValue())) {
                status = "ALREADY_STAGED";
            } else if (!employees.contains(MatchKeys.employee(d.getEmployeeId()))) {
                status = "NO_EMPLOYEE";
            } else {
                List<TargetFolderEntity> candidates = folders.getOrDefault(MatchKeys.key(d.getEmployeeId(), d.getFolderName()), List.of());
                if (candidates.isEmpty()) status = "NO_FOLDER";
                else if (candidates.size() > 1) status = "AMBIGUOUS_FOLDER";
                else {
                    d.setMigId(stage(d, candidates.get(0)));
                    staged.add(d.getDataId().intValue());
                    status = "MATCHED";
                }
            }
            d.setMatchStatus(status);
            summary.add(status);
        }
        sourceDocs.saveAll(batch);
    }

    private Long stage(SourceDocumentEntity d, TargetFolderEntity folder) {
        MigrationEntity m = new MigrationEntity();
        m.setExpedientId(d.getEmployeeId());
        m.setExpedientName(folder.getEmployeeName());
        m.setFolderId(folder.getFolderId().intValue());
        m.setFolderName(folder.getFolderName());
        m.setFileName(d.getName());
        m.setSmartDocType(d.getDocType() != null && !d.getDocType().isBlank() ? d.getDocType() : blankToNull(cfg.getDefaultSmartDocType()));
        m.setSize(d.getSizeBytes() == null ? null : (int) Math.min(d.getSizeBytes(), Integer.MAX_VALUE));
        m.setMimeType(d.getMimeType());
        m.setCreateDate(d.getCreateDate());
        m.setStatus(DocStatus.PENDING.name());
        m = migrations.save(m);

        CsMetadataEntity meta = new CsMetadataEntity();
        meta.setMigId(m.getMigId());
        meta.setDocId(d.getDataId().intValue());
        meta.setDateOfTermination(d.getTerminationDate());
        csMetadata.save(meta);
        return m.getMigId();
    }

    // ------------------------------------------------------------------ helpers

    private ImportRunEntity newRun(Role role, String origin, String user) {
        ImportRunEntity run = new ImportRunEntity();
        run.setMigType(CsToCsType.CODE);
        run.setRole(role.name());
        run.setOrigin(origin);
        run.setStatus("RUNNING");
        run.setUserName(user);
        run.setStartedAt(LocalDateTime.now());
        return runs.save(run);
    }

    private interface Work { int run() throws Exception; }

    private void execute(ImportRunEntity run, Work work) {
        try {
            int n = work.run();
            ImportRunEntity fresh = runs.findById(run.getRunId()).orElse(run);
            fresh.setRowCount(n);
            fresh.setStatus("DONE");
            fresh.setFinishedAt(LocalDateTime.now());
            runs.save(fresh);
        } catch (Exception e) {
            log.error("Import run {} failed", run.getRunId(), e);
            ImportRunEntity fresh = runs.findById(run.getRunId()).orElse(run);
            fresh.setStatus("ERROR");
            fresh.setMessage(e.getMessage());
            fresh.setFinishedAt(LocalDateTime.now());
            runs.save(fresh);
        }
    }

    private void updateCount(ImportRunEntity run, int n) {
        runs.findById(run.getRunId()).ifPresent(r -> { r.setRowCount(n); runs.save(r); });
    }

    private static String col(Map<String, String> row, String name) {
        return row.get(name.toUpperCase());
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String t = s.trim();
            return t.contains(".") ? (long) Double.parseDouble(t) : Long.parseLong(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
