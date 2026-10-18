package com.migration.types.sapal;

/** One row of ItSize in the RFC response: a component of the ArchiveLink document. */
final class SapComponent {
    final String compId;
    final String mimeType;
    final String creaTime;
    /** 1-based, inclusive line range inside ItBinaryobject. */
    final int firstLine;
    final int lastLine;

    SapComponent(String compId, String mimeType, String creaTime, int firstLine, int lastLine) {
        this.compId = compId;
        this.mimeType = mimeType;
        this.creaTime = creaTime;
        this.firstLine = firstLine;
        this.lastLine = lastLine;
    }

    /** v1 rule: the main document is component "data" or "data1". */
    boolean isMain() {
        return mimeType != null && !mimeType.isBlank()
                && ("data".equalsIgnoreCase(compId) || "data1".equalsIgnoreCase(compId));
    }
}
