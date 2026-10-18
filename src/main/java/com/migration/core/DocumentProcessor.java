package com.migration.core;

import com.migration.config.MigrationProperties;
import com.migration.entities.MigrationEntity;
import com.migration.entities.MigrationEventEntity;
import com.migration.entities.SmartTypesEntity;
import com.migration.target.TargetSystem;
import com.migration.repositories.JobRepository;
import com.migration.repositories.MigrationEventRepository;
import com.migration.repositories.MigrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Migrates one document: download, upload (renaming on duplicates), categories, RM classification
 * and hash verification. Every step is recorded in migration_event and the final status reflects
 * the first real problem; a document is MIGRATED only when everything went well.
 *
 * A document that already has doc_id_dest (uploaded in an earlier run) is resumed: it is not
 * uploaded again, only categories, classification and verification are repeated.
 */
@Component
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);
    /** When several post-upload steps fail, the status shows the most serious one. */
    private static final List<DocStatus> FAILURE_PRIORITY = List.of(
            DocStatus.ERROR_HASH_MISMATCH, DocStatus.ERROR_METADATA, DocStatus.ERROR_APPLY_CLASSIF, DocStatus.ERROR_HASH_DEST);

    private final MigrationRepository migrations;
    private final MigrationEventRepository events;
    private final JobRepository jobs;
    private final MigrationProperties props;

    public DocumentProcessor(MigrationRepository migrations, MigrationEventRepository events, JobRepository jobs,
                             MigrationProperties props) {
        this.migrations = migrations;
        this.events = events;
        this.jobs = jobs;
        this.props = props;
    }

    public <M> void process(JobRun run, MigrationType<M> type, MigrationEntity doc, M meta, SmartTypesEntity smartType) {
        MDC.put("jobId", String.valueOf(run.jobId));
        MDC.put("migId", String.valueOf(doc.getMigId()));
        Trace trace = new Trace(run, doc.getMigId());
        try (WorkFiles files = workFiles(doc)) {
            DocStatus status = migrate(run, type, doc, meta, smartType, files, trace);
            doc.setStatus(status.name());
        } catch (Exception e) {
            log.error("Unexpected error on document {}", doc.getMigId(), e);
            trace.failure(DocStatus.ERROR_UNEXPECTED, "PROCESS", e.toString(), 0);
            doc.setStatus(DocStatus.ERROR_UNEXPECTED.name());
        } finally {
            doc.setErrorDetail(trace.summary());
            migrations.save(doc);
            if (!trace.events.isEmpty()) events.saveAll(trace.events);
            jobs.incrementProcessed(run.jobId);
            MDC.remove("migId");
        }
    }

    private <M> DocStatus migrate(JobRun run, MigrationType<M> type, MigrationEntity doc, M meta,
                                  SmartTypesEntity smartType, WorkFiles files, Trace trace) {
        if (meta == null) {
            return trace.failure(DocStatus.ERROR_GET_MIGID, "METADATA",
                    "No row in " + type.metadataTable() + " for mig_id " + doc.getMigId(), 0);
        }
        boolean hasType = doc.getSmartDocType() != null && !doc.getSmartDocType().isBlank();
        if (smartType == null && (hasType || type.requiresSmartType())) {
            return trace.failure(DocStatus.ERROR_SMART_TYPE, "METADATA",
                    "smart_doc_type '" + doc.getSmartDocType() + "' not found in smartdoctypes", 0);
        }

        if (doc.getTargetDocId() == null) {
            DocStatus uploadProblem = downloadAndUpload(run, type, doc, meta, files, trace);
            if (uploadProblem != null) return uploadProblem;
        } else {
            trace.ok("RESUME", "Already uploaded as node " + doc.getTargetDocId() + ", repeating post-upload steps", 0);
        }
        long nodeId = doc.getTargetDocId();

        for (CategoryValues category : type.categories(doc, meta, smartType)) {
            long t = System.currentTimeMillis();
            try {
                run.target.applyCategory(nodeId, category);
                trace.ok("CATEGORY", category.name + " (" + category.categoryId + ") applied", elapsed(t));
            } catch (RuntimeException e) {
                trace.failure(DocStatus.ERROR_METADATA, "CATEGORY", category.name + ": " + e.getMessage(), elapsed(t));
            }
        }

        Integer classId = smartType == null ? null : smartType.getClassId();
        if (classId != null && classId != 0) {
            long t = System.currentTimeMillis();
            try {
                run.target.applyClassification(nodeId, classId);
                trace.ok("CLASSIFICATION", "Class " + classId + " applied", elapsed(t));
            } catch (RuntimeException e) {
                trace.failure(DocStatus.ERROR_APPLY_CLASSIF, "CLASSIFICATION", e.getMessage(), elapsed(t));
            }
        }

        verify(run, doc, nodeId, trace);
        return trace.finalStatus();
    }

    /** Returns null when the document is in the target, or the error status. */
    private <M> DocStatus downloadAndUpload(JobRun run, MigrationType<M> type, MigrationEntity doc, M meta,
                                            WorkFiles files, Trace trace) {
        long t = System.currentTimeMillis();
        SourceFile file;
        try {
            file = type.download(doc, meta, files);
            if (file == null || file.size == 0) {
                return trace.failure(DocStatus.ERROR_DOWNLOAD, "DOWNLOAD", "Empty content", elapsed(t));
            }
        } catch (Exception e) {
            log.debug("Download failed", e);
            return trace.failure(DocStatus.ERROR_DOWNLOAD, "DOWNLOAD", String.valueOf(e.getMessage()), elapsed(t));
        }
        trace.ok("DOWNLOAD", file.size + " bytes, " + file.mimeType, elapsed(t));
        file.warnings.forEach(w -> trace.warning("DOWNLOAD", w));
        doc.setHashOrigin(file.sha256);
        doc.setHashSource(file.sourceSha256);
        doc.setSize((int) Math.min(file.size, Integer.MAX_VALUE));
        doc.setMimeType(file.mimeType);

        String baseName;
        try {
            baseName = type.targetName(doc, meta);
        } catch (RuntimeException e) {
            return trace.failure(DocStatus.ERROR_UPLOAD, "UPLOAD", "Cannot build the target name: " + e.getMessage(), 0);
        }
        if (doc.getFolderId() == null) {
            return trace.failure(DocStatus.ERROR_UPLOAD, "UPLOAD", "folderid is empty", 0);
        }

        t = System.currentTimeMillis();
        String name = baseName;
        try {
            for (int attempt = 1; ; attempt++) {
                TargetSystem.Upload result = run.target.upload(doc.getFolderId(), name, file.mimeType, file.path);
                if (!result.nameTaken) {
                    doc.setFileName(name);
                    doc.setTargetDocId(Math.toIntExact(result.nodeId));
                    doc.setMigrationDate(LocalDateTime.now());
                    // Saved right away: if anything fails later, a retry will not upload a duplicate.
                    migrations.save(doc);
                    trace.ok("UPLOAD", "Node " + result.nodeId + " '" + name + "'"
                            + (run.logLevel == 3 ? " in " + run.target.describe() + " folder " + doc.getFolderId() : ""), elapsed(t));
                    if (!name.equals(baseName)) trace.warning("UPLOAD", "Renamed to '" + name + "' because the name existed");
                    return null;
                }
                if (attempt > props.getMaxNameAttempts()) {
                    return trace.failure(DocStatus.ERROR_UPLOAD, "UPLOAD",
                            "Name '" + baseName + "' still taken after " + props.getMaxNameAttempts() + " renames", elapsed(t));
                }
                name = type.alternativeName(baseName, attempt);
            }
        } catch (RuntimeException e) {
            return trace.failure(DocStatus.ERROR_UPLOAD, "UPLOAD", e.getMessage(), elapsed(t));
        }
    }

    private void verify(JobRun run, MigrationEntity doc, long nodeId, Trace trace) {
        long t = System.currentTimeMillis();
        try {
            String targetHash = run.target.contentSha256(nodeId);
            doc.setHashTarget(targetHash);
            if (doc.getHashOrigin() == null) {
                trace.warning("HASH_COMPARE", "No origin hash stored, cannot compare");
                return;
            }
            boolean equal = doc.getHashOrigin().equalsIgnoreCase(targetHash);
            doc.setHashCompare(equal ? "HASH EQUAL" : "ERROR DIFFERENT HASH");
            if (equal) {
                trace.ok("HASH_COMPARE", "Hashes are equal", elapsed(t));
            } else {
                trace.failure(DocStatus.ERROR_HASH_MISMATCH, "HASH_COMPARE",
                        "Origin " + doc.getHashOrigin() + " <> target " + targetHash, elapsed(t));
            }
        } catch (RuntimeException e) {
            trace.failure(DocStatus.ERROR_HASH_DEST, "HASH_DEST", e.getMessage(), elapsed(t));
        }
    }

    private WorkFiles workFiles(MigrationEntity doc) {
        Path dir = props.getWorkDir().isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "migration-tool")
                : Path.of(props.getWorkDir());
        Path dump = props.getDebugDumpDir().isBlank() ? null : Path.of(props.getDebugDumpDir());
        return new WorkFiles(dir, dump, "mig" + doc.getMigId());
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    /** Collects the steps of one document for migration_event, the log file and error_detail. */
    static final class Trace {
        private final JobRun run;
        private final Long migId;
        final List<MigrationEventEntity> events = new ArrayList<>();
        private final List<DocStatus> failures = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();
        private boolean warnings;

        Trace(JobRun run, Long migId) {
            this.run = run;
            this.migId = migId;
        }

        void ok(String step, String message, long ms) {
            if (run.logSteps()) {
                log.info("[DOC {}] {} OK {} ({} ms)", migId, step, message, ms);
                events.add(new MigrationEventEntity(run.jobId, migId, step, "SUCCESS", message, ms));
            }
        }

        void warning(String step, String message) {
            warnings = true;
            messages.add("WARN " + step + ": " + message);
            if (run.logLevel != 1) log.warn("[DOC {}] {} {}", migId, step, message);
            events.add(new MigrationEventEntity(run.jobId, migId, step, "WARNING", message, 0));
        }

        DocStatus failure(DocStatus status, String step, String message, long ms) {
            failures.add(status);
            messages.add(status.name() + " " + step + ": " + message);
            log.error("[DOC {}] {} {} {}", migId, step, status, message);
            events.add(new MigrationEventEntity(run.jobId, migId, step, status.name(), message, ms));
            return status;
        }

        DocStatus finalStatus() {
            for (DocStatus s : FAILURE_PRIORITY) {
                if (failures.contains(s)) return s;
            }
            if (!failures.isEmpty()) return failures.get(0);
            return warnings ? DocStatus.MIGRATED_WITH_WARNINGS : DocStatus.MIGRATED;
        }

        String summary() {
            if (messages.isEmpty()) return null;
            String joined = String.join(" | ", messages);
            return joined.length() > 2000 ? joined.substring(0, 1999) + "…" : joined;
        }
    }
}
