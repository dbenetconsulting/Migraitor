package com.migration.core;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Status of a document in table migration. Stored as text; the names of v1 statuses are unchanged.
 * Failures are ordered by priority: when several steps fail, the first one in this list wins.
 */
public enum DocStatus {
    PENDING,
    IN_PROGRESS,
    MIGRATED,
    /** Migrated and verified, with something worth reviewing (e.g. CCITT reconstruction). */
    MIGRATED_WITH_WARNINGS,

    ERROR_GET_MIGID,
    ERROR_SMART_TYPE,
    ERROR_DOWNLOAD,
    ERROR_HASH_ORIGIN,
    ERROR_UPLOAD,
    ERROR_HASH_MISMATCH,
    ERROR_HASH_DEST,
    ERROR_METADATA,
    ERROR_APPLY_CLASSIF,
    /** The application stopped while the document was being processed after upload. */
    ERROR_INTERRUPTED,
    ERROR_UNEXPECTED;

    /** Statuses that "retry job" puts back in the queue. Hash mismatches need a manual decision. */
    public static final Set<DocStatus> RETRYABLE = EnumSet.of(
            ERROR_GET_MIGID, ERROR_SMART_TYPE, ERROR_DOWNLOAD, ERROR_HASH_ORIGIN, ERROR_UPLOAD,
            ERROR_HASH_DEST, ERROR_METADATA, ERROR_APPLY_CLASSIF, ERROR_INTERRUPTED, ERROR_UNEXPECTED);

    /** v1 statuses that may still exist in old databases and are also retried. */
    public static final List<String> LEGACY_RETRYABLE = List.of("ERROR", "ABORTED");

    public boolean isError() {
        return name().startsWith("ERROR_");
    }

    public static List<String> retryableNames() {
        List<String> names = RETRYABLE.stream().map(Enum::name).collect(Collectors.toList());
        names.addAll(LEGACY_RETRYABLE);
        return names;
    }

    /** Every error status, plus the v1 ones. */
    public static List<String> errorNames() {
        List<String> names = Arrays.stream(values()).filter(DocStatus::isError).map(Enum::name).collect(Collectors.toList());
        names.addAll(LEGACY_RETRYABLE);
        return names;
    }

    public static List<String> names(DocStatus... statuses) {
        return Arrays.stream(statuses).map(Enum::name).collect(Collectors.toList());
    }
}
