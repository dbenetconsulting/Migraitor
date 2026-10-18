package com.migration.otcs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.config.HttpClients;
import com.migration.config.MigrationProperties;
import com.migration.core.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Content Server REST client: authentication with ticket reuse and renewal, download, upload,
 * categories, RM classification and LiveReport output. Thread-safe.
 */
@Component
public class OtcsClient {

    private static final Logger log = LoggerFactory.getLogger(OtcsClient.class);
    /** Messages Content Server returns (in several UI languages) when the name is taken. */
    private static final List<String> NAME_TAKEN = List.of("already exists", "existiert bereits", "ya existe", "existe déjà");

    private final HttpClients httpClients;
    private final MigrationProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public OtcsClient(HttpClients httpClients, MigrationProperties props) {
        this.httpClients = httpClients;
        this.props = props;
    }

    private static final class Ticket {
        final String value;
        final Instant issued;

        Ticket(String value) {
            this.value = value;
            this.issued = Instant.now();
        }
    }

    // ------------------------------------------------------------------ authentication

    public String ticket(OtcsEndpoint ep) {
        Ticket t = tickets.get(ep.key());
        if (t != null && Duration.between(t.issued, Instant.now()).compareTo(props.getTicketTtl()) < 0) {
            return t.value;
        }
        synchronized (tickets) {
            t = tickets.get(ep.key());
            if (t != null && Duration.between(t.issued, Instant.now()).compareTo(props.getTicketTtl()) < 0) {
                return t.value;
            }
            Ticket fresh = new Ticket(authenticate(ep));
            tickets.put(ep.key(), fresh);
            return fresh.value;
        }
    }

    public void invalidate(OtcsEndpoint ep) {
        tickets.remove(ep.key());
    }

    private String authenticate(OtcsEndpoint ep) {
        String body = "username=" + enc(ep.username()) + "&password=" + enc(ep.password());
        HttpRequest request = HttpRequest.newBuilder(URI.create(ep.baseUrl() + "/api/v1/auth"))
                .timeout(props.getHttpConnectTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = send(ep, request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new OtcsException("Authentication failed on " + ep.name() + ": HTTP " + response.statusCode()
                    + " " + errorMessage(response.body()), response.statusCode());
        }
        try {
            JsonNode node = json.readTree(response.body()).path("ticket");
            if (node.isMissingNode() || node.asText().isEmpty()) {
                throw new OtcsException("No ticket in authentication response from " + ep.name(), response.statusCode());
            }
            log.info("Authenticated on {}", ep);
            return node.asText();
        } catch (IOException e) {
            throw new OtcsException("Invalid authentication response from " + ep.name(), e);
        }
    }

    /** Checks URL and credentials; returns null when OK or the error message. */
    public String test(OtcsEndpoint ep) {
        try {
            invalidate(ep);
            ticket(ep);
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    // ------------------------------------------------------------------ content

    /** Result of streaming a document's content. */
    public static final class ContentInfo {
        public final long size;
        public final String sha256;
        public final String contentType;

        ContentInfo(long size, String sha256, String contentType) {
            this.size = size;
            this.sha256 = sha256;
            this.contentType = contentType;
        }
    }

    /** Streams the content of a node into target (may be null to only hash it). */
    public ContentInfo downloadContent(OtcsEndpoint ep, long nodeId, Path target) {
        String url = ep.baseUrl() + "/api/" + ep.edition().contentVersion + "/nodes/" + nodeId + "/content";
        HttpResponse<InputStream> response = withTicket(ep, ticket -> HttpRequest.newBuilder(URI.create(url))
                .timeout(props.getHttpRequestTimeout())
                .header("OTCSTicket", ticket)
                .GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() / 100 != 2) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                throw new OtcsException("Download of node " + nodeId + " failed: HTTP " + response.statusCode()
                        + " " + errorMessage(body), response.statusCode());
            }
            CountingOutputStream counter = new CountingOutputStream(target == null ? null : Files.newOutputStream(target));
            String sha;
            try (counter) {
                sha = Hashing.copyAndHash(in, counter);
            }
            String type = response.headers().firstValue("Content-Type").orElse(null);
            return new ContentInfo(counter.count, sha, type);
        } catch (IOException e) {
            throw new OtcsException("Error reading content of node " + nodeId + ": " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ upload

    /** Outcome of an upload attempt: either the new node id or "name already taken". */
    public static final class UploadResult {
        public final Long nodeId;
        public final boolean nameTaken;

        private UploadResult(Long nodeId, boolean nameTaken) {
            this.nodeId = nodeId;
            this.nameTaken = nameTaken;
        }
    }

    /** Creates a document (subtype 144) under parentId with the content of file. */
    public UploadResult uploadDocument(OtcsEndpoint ep, long parentId, String name, String mimeType, Path file) {
        String boundary = "----MigrationTool" + UUID.randomUUID().toString().replace("-", "");
        String url = ep.baseUrl() + "/api/" + ep.edition().nodesVersion + "/nodes";
        byte[] head = multipartHead(boundary, parentId, name, mimeType);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = withTicket(ep, ticket -> HttpRequest.newBuilder(URI.create(url))
                .timeout(props.getHttpRequestTimeout())
                .header("OTCSTicket", ticket)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                // The supplier re-opens the file, so the body can be re-sent after a ticket renewal.
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> {
                    try {
                        return new SequenceInputStream(new ByteArrayInputStream(head),
                                new SequenceInputStream(Files.newInputStream(file), new ByteArrayInputStream(tail)));
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }))
                .build(), HttpResponse.BodyHandlers.ofString());

        String body = response.body() == null ? "" : response.body();
        if (response.statusCode() / 100 == 2) {
            Long id = parseNodeId(body);
            if (id == null) {
                throw new OtcsException("Upload returned no node id: " + abbreviate(body), response.statusCode());
            }
            return new UploadResult(id, false);
        }
        if ((response.statusCode() == 400 || response.statusCode() == 500) && isNameTaken(body)) {
            return new UploadResult(null, true);
        }
        throw new OtcsException("Upload failed: HTTP " + response.statusCode() + " " + errorMessage(body), response.statusCode());
    }

    static byte[] multipartHead(String boundary, long parentId, String name, String mimeType) {
        String crlf = "\r\n";
        StringBuilder sb = new StringBuilder();
        part(sb, boundary, "type", "144");
        part(sb, boundary, "parent_id", String.valueOf(parentId));
        part(sb, boundary, "name", name);
        sb.append("--").append(boundary).append(crlf)
                .append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(name.replace("\"", "'")).append("\"").append(crlf)
                .append("Content-Type: ").append(mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType).append(crlf)
                .append(crlf);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void part(StringBuilder sb, String boundary, String field, String value) {
        sb.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(field).append("\"\r\n\r\n")
                .append(value).append("\r\n");
    }

    static boolean isNameTaken(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        return NAME_TAKEN.stream().anyMatch(lower::contains);
    }

    /** v1 returns {"id":123}; v2 returns {"results":{"data":{"properties":{"id":123}}}}. */
    Long parseNodeId(String body) {
        try {
            JsonNode root = json.readTree(body);
            JsonNode[] candidates = {
                    root.path("results").path("data").path("properties").path("id"),
                    root.path("results").path(0).path("data").path("properties").path("id"),
                    root.path("id")
            };
            for (JsonNode c : candidates) {
                if (c.canConvertToLong() && c.asLong() > 0) return c.asLong();
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ categories & RM

    /** Applies a category with its attribute values (form fields {categoryId}_{attrId}). */
    public void applyCategory(OtcsEndpoint ep, long nodeId, int categoryId, Map<Integer, String> attributes) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("category_id", String.valueOf(categoryId));
        attributes.forEach((attr, value) -> {
            if (value != null) form.put(categoryId + "_" + attr, value);
        });
        String url = ep.baseUrl() + "/api/" + ep.edition().categoriesVersion + "/nodes/" + nodeId + "/categories";
        try {
            sendForm(ep, "POST", url, form, "Category " + categoryId);
        } catch (OtcsException created) {
            if (created.status() != 400 && created.status() != 500) throw created;
            // The category may already be on the node (e.g. when a document is resumed): update it instead.
            form.remove("category_id");
            try {
                sendForm(ep, "PUT", url + "/" + categoryId, form, "Category " + categoryId);
            } catch (OtcsException updated) {
                throw new OtcsException(created.getMessage() + " / update: " + updated.getMessage(), updated.status());
            }
        }
    }

    /** Assigns an RM classification (always REST v1, as in v1 of the tool). */
    public void applyClassification(OtcsEndpoint ep, long nodeId, int classId) {
        String url = ep.baseUrl() + "/api/v1/nodes/" + nodeId + "/classifications";
        sendForm(ep, "POST", url, Map.of("class_id", "[" + classId + "]"), "Classification " + classId);
    }

    private void sendForm(OtcsEndpoint ep, String method, String url, Map<String, String> form, String what) {
        StringJoiner body = new StringJoiner("&");
        form.forEach((k, v) -> body.add(enc(k) + "=" + enc(v)));
        HttpResponse<String> response = withTicket(ep, ticket -> HttpRequest.newBuilder(URI.create(url))
                .timeout(props.getHttpRequestTimeout())
                .header("OTCSTicket", ticket)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .method(method, HttpRequest.BodyPublishers.ofString(body.toString()))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new OtcsException(what + ": HTTP " + response.statusCode() + " " + errorMessage(response.body()), response.statusCode());
        }
    }

    // ------------------------------------------------------------------ reports

    /**
     * Runs a WebReport through the standard REST API and returns its JSON output.
     * Content Server REST cannot run a LiveReport directly: the LiveReport (or its SQL) is the data
     * source of a WebReport whose reportview writes JSON rows, and the WebReport is called with
     * GET api/v1/nodes/{id}/output (or api/v1/webreports/{nickname}). Inputs go in the query string
     * with the names the WebReport defines (see {@link #reportParameters}).
     * With format=webreport the body is the reportview itself; with format=json Content Server wraps
     * it as {"data": "..."} and the string is parsed here.
     */
    public JsonNode runReport(OtcsEndpoint ep, String pathTemplate, String reportRef, Map<String, String> params) {
        StringJoiner query = new StringJoiner("&");
        params.forEach((k, v) -> query.add(enc(k) + "=" + enc(v)));
        String path = pathTemplate.replace("{id}", enc(reportRef)).replace("{nickname}", enc(reportRef));
        String url = ep.baseUrl() + path + (path.contains("?") ? "&" : "?") + query;
        HttpResponse<String> response = withTicket(ep, ticket -> HttpRequest.newBuilder(URI.create(url))
                .timeout(props.getHttpRequestTimeout())
                .header("OTCSTicket", ticket)
                .header("Accept", "application/json")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new OtcsException("WebReport " + reportRef + ": HTTP " + response.statusCode() + " " + errorMessage(response.body()), response.statusCode());
        }
        try {
            JsonNode root = json.readTree(response.body());
            JsonNode data = root.get("data");
            if (data != null && data.isTextual()) {
                String inner = data.asText().trim();
                root = inner.isEmpty() ? json.createArrayNode() : json.readTree(inner);
            }
            return root;
        } catch (IOException e) {
            throw new OtcsException("WebReport " + reportRef + " did not return JSON (check its reportview): " + abbreviate(response.body()), response.statusCode());
        }
    }

    /** Input parameters a WebReport expects (GET api/v1/nodes/{id}/parameters), to check the configuration. */
    public JsonNode reportParameters(OtcsEndpoint ep, String reportRef) {
        String url = ep.baseUrl() + "/api/v1/nodes/" + enc(reportRef) + "/parameters";
        HttpResponse<String> response = withTicket(ep, ticket -> HttpRequest.newBuilder(URI.create(url))
                .timeout(props.getHttpRequestTimeout())
                .header("OTCSTicket", ticket)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new OtcsException("WebReport " + reportRef + " parameters: HTTP " + response.statusCode() + " " + errorMessage(response.body()), response.statusCode());
        }
        try {
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new OtcsException("WebReport parameters are not JSON: " + abbreviate(response.body()), response.statusCode());
        }
    }

    // ------------------------------------------------------------------ plumbing

    /** Sends a request with a ticket; on 401 renews the ticket once and resends. */
    private <T> HttpResponse<T> withTicket(OtcsEndpoint ep, Function<String, HttpRequest> request, HttpResponse.BodyHandler<T> handler) {
        HttpResponse<T> response = send(ep, request.apply(ticket(ep)), handler);
        if (response.statusCode() == 401) {
            log.info("Ticket rejected by {}, renewing", ep.name());
            closeQuietly(response);
            invalidate(ep);
            response = send(ep, request.apply(ticket(ep)), handler);
        }
        return response;
    }

    private <T> HttpResponse<T> send(OtcsEndpoint ep, HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return httpClients.get(ep.useProxy()).send(request, handler);
        } catch (IOException e) {
            throw new OtcsException("Cannot reach " + ep.name() + " (" + request.uri().getHost() + "): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OtcsException("Interrupted calling " + ep.name(), e);
        }
    }

    private static void closeQuietly(HttpResponse<?> response) {
        if (response.body() instanceof InputStream) {
            try {
                ((InputStream) response.body()).close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }

    String errorMessage(String body) {
        if (body == null || body.isBlank()) return "(empty response)";
        try {
            JsonNode root = json.readTree(body);
            if (root.hasNonNull("error")) return root.get("error").isTextual() ? root.get("error").asText() : root.get("error").toString();
            if (root.hasNonNull("message")) return root.get("message").asText();
        } catch (IOException ignored) {
            // not JSON
        }
        return abbreviate(body);
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "…" : flat;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        long count;

        CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            count++;
            if (delegate != null) delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            count += len;
            if (delegate != null) delegate.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (delegate != null) delegate.close();
        }
    }
}
