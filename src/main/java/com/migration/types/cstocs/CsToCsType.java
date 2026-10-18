package com.migration.types.cstocs;

import com.migration.core.CategoryValues;
import com.migration.core.MigrationType;
import com.migration.core.SourceFile;
import com.migration.core.WorkFiles;
import com.migration.entities.CsMetadataEntity;
import com.migration.entities.MigrationEntity;
import com.migration.entities.SmartTypesEntity;
import com.migration.otcs.ConnectionService;
import com.migration.otcs.OtcsClient;
import com.migration.repositories.CsMetadataRepository;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Content Server (on-premise) to Content Server (cloud), used for Deichmann.
 * The document keeps its name and goes to the folder found by the import/match step (folderid).
 */
@Component
public class CsToCsType implements MigrationType<CsMetadataEntity> {

    public static final String CODE = "CStoCS";

    private final CsMetadataRepository metadata;
    private final OtcsClient otcs;
    private final ConnectionService connections;
    private final CsToCsProperties props;

    public CsToCsType(CsMetadataRepository metadata, OtcsClient otcs, ConnectionService connections, CsToCsProperties props) {
        this.metadata = metadata;
        this.otcs = otcs;
        this.connections = connections;
        this.props = props;
    }

    @Override public String code() { return CODE; }
    @Override public String displayName() { return "Content Server → Content Server"; }
    @Override public String metadataTable() { return "cs_metadata"; }
    @Override public boolean supportsImport() { return true; }
    @Override public boolean requiresSmartType() { return false; }

    @Override
    public Map<Long, CsMetadataEntity> loadMetadata(Collection<Long> migIds) {
        return metadata.findByMigIdIn(migIds).stream().collect(Collectors.toMap(CsMetadataEntity::getMigId, Function.identity()));
    }

    @Override
    public SourceFile download(MigrationEntity doc, CsMetadataEntity meta, WorkFiles files) throws Exception {
        if (meta.getDocId() == null) throw new IllegalStateException("cs_metadata.doc_id is empty");
        Path file = files.newFile(".bin");
        OtcsClient.ContentInfo info = otcs.downloadContent(
                connections.endpoint(CODE, ConnectionService.Role.SOURCE), meta.getDocId(), file);
        files.dump(file, doc.getMigId() + "_" + doc.getFileName());
        String mime = doc.getMimeType() != null && !doc.getMimeType().isBlank() ? doc.getMimeType() : stripParams(info.contentType);
        return new SourceFile(file, info.size, mime, info.sha256, info.sha256);
    }

    @Override
    public String targetName(MigrationEntity doc, CsMetadataEntity meta) {
        if (doc.getFileName() == null || doc.getFileName().isBlank()) throw new IllegalStateException("filename is empty");
        return doc.getFileName();
    }

    /** Same rule as v1: name_{timestamp}.ext */
    @Override
    public String alternativeName(String baseName, int attempt) {
        String stamp = String.valueOf(System.currentTimeMillis()) + (attempt > 1 ? "_" + attempt : "");
        int dot = baseName.lastIndexOf('.');
        return dot > 0 ? baseName.substring(0, dot) + "_" + stamp + baseName.substring(dot) : baseName + "_" + stamp;
    }

    @Override
    public List<CategoryValues> categories(MigrationEntity doc, CsMetadataEntity meta, SmartTypesEntity smartType) {
        return List.of(
                new CategoryValues("Migration Information", props.getCategoryMigrationInfo())
                        .put(props.getAttrOriginalCreateDate(), doc.getCreateDate())
                        .put(props.getAttrMigrationDate(), LocalDate.now().toString()),
                new CategoryValues("Records Details", props.getCategoryRecordsDetails())
                        .put(props.getAttrDateOfTermination(), meta.getDateOfTermination()));
    }

    private static String stripParams(String contentType) {
        if (contentType == null) return "application/octet-stream";
        int semi = contentType.indexOf(';');
        return (semi > 0 ? contentType.substring(0, semi) : contentType).trim();
    }
}
