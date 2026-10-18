package com.migration.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Temporary files of one document. Everything is deleted when the document is done,
 * so no client document stays on disk (v1 kept copies in S:\MigrationApp\jar).
 */
public final class WorkFiles implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WorkFiles.class);

    private final Path dir;
    private final Path dumpDir;
    private final String prefix;
    private final List<Path> created = new ArrayList<>();

    public WorkFiles(Path dir, Path dumpDir, String prefix) {
        this.dir = dir;
        this.dumpDir = dumpDir;
        this.prefix = prefix;
    }

    public Path newFile(String suffix) throws IOException {
        Files.createDirectories(dir);
        Path p = Files.createTempFile(dir, prefix + "-", suffix);
        created.add(p);
        return p;
    }

    /** Debug only (migration.debug-dump-dir): keeps a copy of a file for inspection. */
    public void dump(Path file, String name) {
        if (dumpDir == null) return;
        try {
            Files.createDirectories(dumpDir);
            Files.copy(file, dumpDir.resolve(name.replaceAll("[\\\\/:*?\"<>|]", "_")), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Could not write debug copy {}: {}", name, e.getMessage());
        }
    }

    @Override
    public void close() {
        for (Path p : created) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("Could not delete temporary file {}: {}", p, e.getMessage());
            }
        }
    }
}
