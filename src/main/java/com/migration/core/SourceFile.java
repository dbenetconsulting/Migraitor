package com.migration.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The binary ready to upload, with its hashes. */
public final class SourceFile {

    public final Path path;
    public final long size;
    public final String mimeType;
    /** SHA-256 of the file that will be uploaded. */
    public final String sha256;
    /** SHA-256 of the raw binary from the source system (same as sha256 unless converted). */
    public final String sourceSha256;
    public final List<String> warnings = new ArrayList<>();

    public SourceFile(Path path, long size, String mimeType, String sha256, String sourceSha256) {
        this.path = path;
        this.size = size;
        this.mimeType = mimeType;
        this.sha256 = sha256;
        this.sourceSha256 = sourceSha256;
    }

    public SourceFile warn(String warning) {
        warnings.add(warning);
        return this;
    }
}
