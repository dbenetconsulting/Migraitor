package com.migration.types.cstocs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration of the Content Server to Content Server type (Deichmann). Defaults = v1 values. */
@ConfigurationProperties(prefix = "migration.cstocs")
public class CsToCsProperties {

    /** Category "Migration Information": attribute 2 = original create date, 3 = migration date. */
    private int categoryMigrationInfo = 30447;
    private int attrOriginalCreateDate = 2;
    private int attrMigrationDate = 3;
    /** Category "Records Details": attribute 2 = date of termination. */
    private int categoryRecordsDetails = 17526543;
    private int attrDateOfTermination = 2;

    private final Import importer = new Import();

    /** How the source export (documents) and the target export (folders) are read and matched. */
    public static class Import {
        /**
         * Standard REST call that runs the WebReport wrapping the LiveReport. {id} = WebReport DataID.
         * Alternative: /api/v1/webreports/{nickname}?format=webreport when the report has a nickname.
         */
        private String reportPath = "/api/v1/nodes/{id}/output?format=webreport";
        /** WebReport input with the last DataID already read (keyset paging). */
        private String cursorParam = "inputLabel1";
        /** WebReport input with the page size. */
        private String pageSizeParam = "inputLabel2";
        private int pageSize = 1000;
        private int maxPages = 10_000;
        /** Name of the folder level that exists only in the target, between employee and folders. */
        private String targetParentFolder = "999 Altakte";
        /** smart_doc_type written to staging when the export has no document type column. */
        private String defaultSmartDocType = "";

        // Column names in the source export
        private String colDataId = "DATAID";
        private String colEmployeeId = "EMPLOYEEID";
        private String colName = "NAME";
        private String colDocType = "DOCTYPE";
        private String colSize = "DATASIZE";
        private String colMimeType = "MIMETYPE";
        private String colCreateDate = "CREATEDATE";
        private String colFolderName = "FOLDERNAME";
        private String colParentId = "PARENTID";
        private String colTerminationDate = "DATEOFTERMINATION";

        // Column names in the target export
        private String colTargetEmployeeId = "EMPLOYEEID";
        private String colTargetEmployeeName = "EMPLOYEENAME";
        private String colTargetFolderId = "DATAID";
        private String colTargetFolderName = "NAME";
        private String colTargetParentName = "PARENTNAME";

        public String getReportPath() { return reportPath; }
        public void setReportPath(String v) { reportPath = v; }
        public String getCursorParam() { return cursorParam; }
        public void setCursorParam(String v) { cursorParam = v; }
        public String getPageSizeParam() { return pageSizeParam; }
        public void setPageSizeParam(String v) { pageSizeParam = v; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int v) { pageSize = v; }
        public int getMaxPages() { return maxPages; }
        public void setMaxPages(int v) { maxPages = v; }
        public String getTargetParentFolder() { return targetParentFolder; }
        public void setTargetParentFolder(String v) { targetParentFolder = v; }
        public String getDefaultSmartDocType() { return defaultSmartDocType; }
        public void setDefaultSmartDocType(String v) { defaultSmartDocType = v; }
        public String getColDataId() { return colDataId; }
        public void setColDataId(String v) { colDataId = v; }
        public String getColEmployeeId() { return colEmployeeId; }
        public void setColEmployeeId(String v) { colEmployeeId = v; }
        public String getColName() { return colName; }
        public void setColName(String v) { colName = v; }
        public String getColDocType() { return colDocType; }
        public void setColDocType(String v) { colDocType = v; }
        public String getColSize() { return colSize; }
        public void setColSize(String v) { colSize = v; }
        public String getColMimeType() { return colMimeType; }
        public void setColMimeType(String v) { colMimeType = v; }
        public String getColCreateDate() { return colCreateDate; }
        public void setColCreateDate(String v) { colCreateDate = v; }
        public String getColFolderName() { return colFolderName; }
        public void setColFolderName(String v) { colFolderName = v; }
        public String getColParentId() { return colParentId; }
        public void setColParentId(String v) { colParentId = v; }
        public String getColTerminationDate() { return colTerminationDate; }
        public void setColTerminationDate(String v) { colTerminationDate = v; }
        public String getColTargetEmployeeId() { return colTargetEmployeeId; }
        public void setColTargetEmployeeId(String v) { colTargetEmployeeId = v; }
        public String getColTargetEmployeeName() { return colTargetEmployeeName; }
        public void setColTargetEmployeeName(String v) { colTargetEmployeeName = v; }
        public String getColTargetFolderId() { return colTargetFolderId; }
        public void setColTargetFolderId(String v) { colTargetFolderId = v; }
        public String getColTargetFolderName() { return colTargetFolderName; }
        public void setColTargetFolderName(String v) { colTargetFolderName = v; }
        public String getColTargetParentName() { return colTargetParentName; }
        public void setColTargetParentName(String v) { colTargetParentName = v; }
    }

    public int getCategoryMigrationInfo() { return categoryMigrationInfo; }
    public void setCategoryMigrationInfo(int v) { categoryMigrationInfo = v; }
    public int getAttrOriginalCreateDate() { return attrOriginalCreateDate; }
    public void setAttrOriginalCreateDate(int v) { attrOriginalCreateDate = v; }
    public int getAttrMigrationDate() { return attrMigrationDate; }
    public void setAttrMigrationDate(int v) { attrMigrationDate = v; }
    public int getCategoryRecordsDetails() { return categoryRecordsDetails; }
    public void setCategoryRecordsDetails(int v) { categoryRecordsDetails = v; }
    public int getAttrDateOfTermination() { return attrDateOfTermination; }
    public void setAttrDateOfTermination(int v) { attrDateOfTermination = v; }
    public Import getImporter() { return importer; }
}
