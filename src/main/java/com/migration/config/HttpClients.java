package com.migration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Two shared HTTP clients (with and without the corporate proxy). v1 built a new client,
 * and re-read the secrets, for every call; HttpClient is thread-safe and meant to be reused.
 */
@Component
public class HttpClients {

    private static final Logger log = LoggerFactory.getLogger(HttpClients.class);

    private final MigrationProperties props;
    private final Secrets secrets;
    private volatile HttpClient direct;
    private volatile HttpClient proxied;

    public HttpClients(MigrationProperties props, Secrets secrets) {
        this.props = props;
        this.secrets = secrets;
    }

    public HttpClient get(boolean useProxy) {
        if (useProxy) {
            if (proxied == null) {
                synchronized (this) {
                    if (proxied == null) proxied = build(true);
                }
            }
            return proxied;
        }
        if (direct == null) {
            synchronized (this) {
                if (direct == null) direct = build(false);
            }
        }
        return direct;
    }

    private HttpClient build(boolean useProxy) {
        // HTTP/1.1 on purpose: the h2c upgrade of the JDK client breaks streamed multipart uploads
        // on some servers (stream reset), and Content Server gains nothing from HTTP/2 here.
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.getHttpConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (props.isTrustSelfSigned()) {
            log.warn("migration.trust-self-signed=true: TLS certificates are NOT verified");
            builder.sslContext(trustAll());
        }
        if (useProxy) {
            String host = secrets.get("proxyIp");
            int port = secrets.getInt("proxyPort");
            log.info("HTTP client using proxy {}:{}", host, port);
            builder.proxy(ProxySelector.of(new InetSocketAddress(host, port)));
        }
        return builder.build();
    }

    private static SSLContext trustAll() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
