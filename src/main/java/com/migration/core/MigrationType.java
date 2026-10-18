package com.migration.core;

import com.migration.entities.MigrationEntity;
import com.migration.entities.SmartTypesEntity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * One kind of migration (one source system). The core pipeline does everything that is common
 * (claiming documents, hashing, upload with renaming, categories, RM classification, verification,
 * statuses and logs); a type only says how to read its metadata, how to get the binary and what
 * to write in the target. See docs/NUEVO-TIPO.md.
 *
 * @param <M> metadata entity of the type (one row per document, keyed by mig_id)
 */
public interface MigrationType<M> {

    /** Code stored in jobs.migtype, e.g. "CStoCS". Never change it once used. */
    String code();

    /** Name shown in the UI. */
    String displayName();

    /** Table with this type's metadata. A pending document belongs to the type when it has a row there. */
    String metadataTable();

    /** Metadata of a batch of documents, keyed by mig_id. */
    Map<Long, M> loadMetadata(Collection<Long> migIds);

    /**
     * Downloads the binary to a file from {@code files}. May update doc (mime type, size, create date)
     * when the source knows better than the staging table.
     */
    SourceFile download(MigrationEntity doc, M meta, WorkFiles files) throws Exception;

    /** Name of the document in the target, before any renaming because of duplicates. */
    String targetName(MigrationEntity doc, M meta);

    /** Name to try when {@code baseName} is taken; attempt starts at 1. */
    String alternativeName(String baseName, int attempt);

    /** Categories to apply after upload, in order. */
    List<CategoryValues> categories(MigrationEntity doc, M meta, SmartTypesEntity smartType);

    /**
     * Whether every document needs a smart_doc_type. A smart_doc_type that is set but missing in
     * smartdoctypes is always an error; when this returns false, an empty one just skips classification.
     */
    default boolean requiresSmartType() {
        return true;
    }

    /** Kind of target system (a TargetConnector bean). Every current type writes to Content Server. */
    default String target() {
        return com.migration.target.OtcsTarget.KIND;
    }

    /** Whether the UI offers the "Import" tab (staging from LiveReports or CSV) for this type. */
    default boolean supportsImport() {
        return false;
    }
}
