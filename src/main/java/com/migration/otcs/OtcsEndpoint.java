package com.migration.otcs;

import java.util.Objects;

/** A Content Server instance plus the account used to call it. */
public final class OtcsEndpoint {

    private final String name;
    /** Up to and including the CGI path, e.g. https://host/OTCS/cs.exe or https://host/cs/cs. */
    private final String baseUrl;
    private final Edition edition;
    private final String username;
    private final String password;
    private final boolean useProxy;

    public OtcsEndpoint(String name, String baseUrl, Edition edition, String username, String password, boolean useProxy) {
        this.name = name;
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.edition = edition;
        this.username = username;
        this.password = password;
        this.useProxy = useProxy;
    }

    private static String stripTrailingSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    public String name() { return name; }
    public String baseUrl() { return baseUrl; }
    public Edition edition() { return edition; }
    public String username() { return username; }
    String password() { return password; }
    public boolean useProxy() { return useProxy; }

    /** Cache key for tickets: same server and same user share a ticket. */
    String key() {
        return baseUrl + "|" + username;
    }

    @Override
    public String toString() {
        return name + " (" + baseUrl + ", " + edition + ", user " + username + ")";
    }
}
