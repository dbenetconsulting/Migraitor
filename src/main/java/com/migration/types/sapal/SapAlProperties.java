package com.migration.types.sapal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "migration.sapal")
public class SapAlProperties {

    /** Convert TIFF components to PDF before upload (Viega). Other formats are uploaded as they are. */
    private boolean convertTiffToPdf = false;

    public boolean isConvertTiffToPdf() { return convertTiffToPdf; }
    public void setConvertTiffToPdf(boolean convertTiffToPdf) { this.convertTiffToPdf = convertTiffToPdf; }
}
