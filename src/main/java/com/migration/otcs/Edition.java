package com.migration.otcs;

/**
 * Content Server flavour. It decides the REST API version used for each call,
 * exactly as v1 did (on-premise: v1 everywhere; cloud: v2 except auth and classifications).
 */
public enum Edition {
    ONPREM("/OTCS/cs.exe", "v1", "v1", "v1"),
    CLOUD("/cs/cs", "v2", "v2", "v2");

    /** Path appended to the host when the URL is built from the secrets file. */
    public final String defaultContextPath;
    final String nodesVersion;
    final String contentVersion;
    final String categoriesVersion;

    Edition(String defaultContextPath, String nodesVersion, String contentVersion, String categoriesVersion) {
        this.defaultContextPath = defaultContextPath;
        this.nodesVersion = nodesVersion;
        this.contentVersion = contentVersion;
        this.categoriesVersion = categoriesVersion;
    }

    public static Edition parse(String value) {
        return value != null && value.trim().equalsIgnoreCase("cloud") ? CLOUD : ONPREM;
    }
}
