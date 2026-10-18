package com.migration.types.sapal;

import com.migration.config.MigrationProperties;
import com.migration.config.Secrets;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

/**
 * Calls the SOAP service ZRfcMigrationOt published in SAP (SOAP Manager) with the physical id
 * (PHIO), the repository and the document class, and stores the XML response in a file.
 * Secrets: sapendpointws, sapusername, sappassword, soapaction.
 */
@Component
public class SapArchiveLinkClient {

    private final Secrets secrets;
    private final HttpClient http;
    private final MigrationProperties props;

    public SapArchiveLinkClient(Secrets secrets, MigrationProperties props) {
        this.secrets = secrets;
        this.props = props;
        // Same network path as v1 (HttpURLConnection honours the JVM proxy settings).
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).proxy(ProxySelector.getDefault()).connectTimeout(props.getHttpConnectTimeout()).build();
    }

    public void fetch(String arcDocId, String archId, String docType, Path target) throws IOException, InterruptedException {
        String auth = Base64.getEncoder().encodeToString(
                (secrets.get("sapusername") + ":" + secrets.get("sappassword")).getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(secrets.get("sapendpointws")))
                .timeout(props.getHttpRequestTimeout())
                .header("Content-Type", "text/xml;charset=UTF-8")
                .header("Authorization", "Basic " + auth)
                .POST(HttpRequest.BodyPublishers.ofString(envelope(arcDocId, archId, docType), StandardCharsets.UTF_8));
        secrets.find("soapaction").ifPresent(a -> request.header("SOAPAction", a));

        HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                String body = new String(in.readNBytes(500), StandardCharsets.UTF_8);
                throw new IOException("SAP HTTP " + response.statusCode() + ": " + body.replaceAll("\\s+", " "));
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String envelope(String arcDocId, String archId, String docType) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "xmlns:urn=\"urn:sap-com:document:sap:soap:functions:mc-style\">"
                + "<soapenv:Header/><soapenv:Body><urn:ZRfcMigrationOt>"
                + "<ItBinaryobject/><ItSize/>"
                + "<IvArcdocid>" + xml(arcDocId) + "</IvArcdocid>"
                + "<IvArchid>" + xml(archId) + "</IvArchid>"
                + "<IvDoctype>" + xml(docType) + "</IvDoctype>"
                + "</urn:ZRfcMigrationOt></soapenv:Body></soapenv:Envelope>";
    }

    private static String xml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
