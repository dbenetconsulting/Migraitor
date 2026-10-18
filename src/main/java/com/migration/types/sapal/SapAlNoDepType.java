package com.migration.types.sapal;

import com.migration.config.Secrets;
import com.migration.core.CategoryValues;
import com.migration.core.Hashing;
import com.migration.core.MigrationType;
import com.migration.core.SourceFile;
import com.migration.core.WorkFiles;
import com.migration.entities.MigrationEntity;
import com.migration.entities.SapAlMetadataEntity;
import com.migration.entities.SmartTypesEntity;
import com.migration.repositories.SapAlMetadataRepository;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SAP HCM ArchiveLink (no table dependency) to Content Server, used for Viega.
 * The binary comes from the RFC ZRfcMigrationOt exposed as SOAP; TIFF components can be converted
 * to PDF (migration.sapal.convert-tiff-to-pdf). Category ids come from the same secrets keys as v1.
 */
@Component
public class SapAlNoDepType implements MigrationType<SapAlMetadataEntity> {

    public static final String CODE = "SAPALNoDep";

    private static final DateTimeFormatter DB_DATE = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
            .toFormatter();
    private static final DateTimeFormatter NAME_DATE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter SAP_CREA = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SapAlMetadataRepository metadata;
    private final SapArchiveLinkClient sap;
    private final SapAlProperties props;
    private final Secrets secrets;

    public SapAlNoDepType(SapAlMetadataRepository metadata, SapArchiveLinkClient sap, SapAlProperties props, Secrets secrets) {
        this.metadata = metadata;
        this.sap = sap;
        this.props = props;
        this.secrets = secrets;
    }

    @Override public String code() { return CODE; }
    @Override public String displayName() { return "SAP ArchiveLink → Content Server"; }
    @Override public String metadataTable() { return "sapalnodep_metadata"; }

    @Override
    public Map<Long, SapAlMetadataEntity> loadMetadata(Collection<Long> migIds) {
        return metadata.findByMigIdIn(migIds).stream().collect(Collectors.toMap(SapAlMetadataEntity::getMigId, Function.identity()));
    }

    @Override
    public SourceFile download(MigrationEntity doc, SapAlMetadataEntity meta, WorkFiles files) throws Exception {
        Path xml = files.newFile(".xml");
        sap.fetch(meta.getPhio(), meta.getRepository(), meta.getSapDocType(), xml);
        files.dump(xml, doc.getMigId() + "_response.xml");

        Path raw = files.newFile(".bin");
        SapResponseParser.Result r = SapResponseParser.parse(xml, files.newFile(".b64"), raw);
        Files.deleteIfExists(xml);
        if (r.size == 0) throw new IllegalStateException("SAP returned an empty document");

        if (blank(doc.getCreateDate()) && !blank(r.component.creaTime)) doc.setCreateDate(fromSapTime(r.component.creaTime));
        String mime = !blank(doc.getMimeType()) ? doc.getMimeType()
                : !blank(r.component.mimeType) ? r.component.mimeType : "application/octet-stream";

        boolean tiff = mime.toLowerCase().contains("tif") || TiffToPdfConverter.isTiff(raw);
        if (!props.isConvertTiffToPdf() || !tiff) {
            return new SourceFile(raw, r.size, mime, r.sha256, r.sha256);
        }

        Path pdf = files.newFile(".pdf");
        TiffToPdfConverter.Result conv = TiffToPdfConverter.convert(raw, pdf);
        files.dump(pdf, doc.getMigId() + ".pdf");
        String pdfHash;
        try (InputStream in = Files.newInputStream(pdf)) {
            pdfHash = Hashing.copyAndHash(in, java.io.OutputStream.nullOutputStream());
        }
        SourceFile out = new SourceFile(pdf, Files.size(pdf), "application/pdf", pdfHash, r.sha256);
        if (conv.reconstructed) {
            out.warn("TIFF converted after rebuilding the CCITT header (" + conv.pages + " page(s)); check the PDF visually");
        }
        return out;
    }

    /** v1 rule: First_Last_EmpId_yyyyMMdd_HHmmss, without extension; an existing filename wins. */
    @Override
    public String targetName(MigrationEntity doc, SapAlMetadataEntity meta) {
        if (!blank(doc.getFileName())) return doc.getFileName();
        if (blank(doc.getCreateDate())) throw new IllegalStateException("createdate is empty, cannot build the name");
        LocalDateTime created = LocalDateTime.parse(doc.getCreateDate().trim(), DB_DATE);
        return meta.getFirstName() + "_" + meta.getLastName() + "_" + meta.getEmployeeId() + "_" + created.format(NAME_DATE);
    }

    /** v1 rule (FilenameDuplicated): base_1, base_2, ... */
    @Override
    public String alternativeName(String baseName, int attempt) {
        return baseName + "_" + attempt;
    }

    @Override
    public List<CategoryValues> categories(MigrationEntity doc, SapAlMetadataEntity meta, SmartTypesEntity smartType) {
        List<CategoryValues> cats = new ArrayList<>();
        cats.add(new CategoryValues("Migration", secrets.getInt("catmigration"))
                .put(secrets.getInt("catmigrationdateofOrigin"), meta.getDateOfOrigin())
                .put(secrets.getInt("catmigrationdateofTermination"), meta.getDateOfTermination())
                .put(secrets.getInt("catmigrationdocumentType"), smartType == null ? null : smartType.getClassNickname())
                .put(secrets.getInt("catmigrationpersonID"), meta.getEmployeeId()));
        // Inactive employees are managed in SuccessFactors: no termination date in Record Details.
        if (!"inactive".equalsIgnoreCase(meta.getStatus())) {
            CategoryValues record = new CategoryValues("Record Details", secrets.getInt("catrecordDetails"))
                    .put(secrets.getInt("catrecordDetailsdateofTermination"), meta.getDateOfTermination());
            if (!record.attributes.isEmpty()) cats.add(record);
        }
        cats.add(new CategoryValues("Document Type Details", secrets.getInt("catdocumentTypeDetails"))
                .put(secrets.getInt("catdocumentTypeDetailsdateofOrigin"), meta.getDateOfOrigin()));
        return cats;
    }

    static String fromSapTime(String creaTime) {
        String digits = creaTime.replaceAll("\\D", "");
        if (digits.length() < 14) return creaTime;
        return LocalDateTime.parse(digits.substring(0, 14), SAP_CREA).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
