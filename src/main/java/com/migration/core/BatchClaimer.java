package com.migration.core;

import com.migration.config.MigrationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Reserves pending documents for one job in a single atomic UPDATE, so two jobs never pick the
 * same document (v1 read "PENDING" rows without reserving them) and a job only gets documents of
 * its own migration type.
 */
@Component
public class BatchClaimer {

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbc;
    private final MigrationProperties props;

    public BatchClaimer(JdbcTemplate jdbc, MigrationProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    /** Moves up to {@code max} PENDING documents of the type to IN_PROGRESS for the job; returns how many. */
    @Transactional
    public synchronized int claim(long jobId, String metadataTable, int max) {
        if (!TABLE_NAME.matcher(metadataTable).matches()) {
            throw new IllegalArgumentException("Invalid metadata table name: " + metadataTable);
        }
        String belongs = "EXISTS (SELECT 1 FROM " + metadataTable + " t WHERE t.mig_id = m.mig_id)";
        if ("sqlserver".equalsIgnoreCase(props.getDbType())) {
            return jdbc.update("UPDATE TOP (?) m SET m.status = 'IN_PROGRESS', m.job_id = ? "
                    + "FROM migration m WITH (UPDLOCK, READPAST, ROWLOCK) "
                    + "WHERE UPPER(m.status) = 'PENDING' AND " + belongs, max, jobId);
        }
        // PostgreSQL skips rows locked by another job; other databases (H2 in tests) rely on this
        // method being synchronized, which is enough for a single application instance.
        String lock = "postgresql".equalsIgnoreCase(props.getDbType()) ? " FOR UPDATE SKIP LOCKED" : "";
        return jdbc.update("UPDATE migration SET status = 'IN_PROGRESS', job_id = ? WHERE mig_id IN ("
                + "SELECT m.mig_id FROM migration m WHERE UPPER(m.status) = 'PENDING' AND " + belongs
                + " ORDER BY m.mig_id LIMIT ?" + lock + ")", jobId, max);
    }
}
