package com.migration.otcs;

import com.migration.config.ConfigCrypto;
import com.migration.config.MigrationProperties;
import com.migration.config.Secrets;
import com.migration.entities.CsConnectionEntity;
import com.migration.repositories.CsConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Resolves the Content Server endpoints of each migration type. A connection saved from the UI
 * (table cs_connection) wins; otherwise the v1 keys of the secrets file are used, so existing
 * installations keep working without any UI configuration.
 */
@Service
public class ConnectionService {

    public enum Role { SOURCE, TARGET }

    private final CsConnectionRepository repository;
    private final ConfigCrypto crypto;
    private final Secrets secrets;
    private final MigrationProperties props;

    public ConnectionService(CsConnectionRepository repository, ConfigCrypto crypto, Secrets secrets, MigrationProperties props) {
        this.repository = repository;
        this.crypto = crypto;
        this.secrets = secrets;
        this.props = props;
    }

    public OtcsEndpoint endpoint(String migType, Role role) {
        return repository.findByMigTypeAndRole(migType, role.name())
                .map(this::fromEntity)
                .orElseGet(() -> fromSecrets(migType, role));
    }

    public Optional<CsConnectionEntity> find(String migType, Role role) {
        return repository.findByMigTypeAndRole(migType, role.name());
    }

    public Optional<Long> liveReportId(String migType, Role role) {
        return find(migType, role).map(CsConnectionEntity::getLiveReportId);
    }

    /** Saves (insert or update). A blank password keeps the stored one. */
    @Transactional
    public CsConnectionEntity save(String migType, Role role, String baseUrl, String edition, String username,
                                   String password, Boolean useProxy, Long liveReportId) {
        CsConnectionEntity e = repository.findByMigTypeAndRole(migType, role.name()).orElseGet(CsConnectionEntity::new);
        e.setMigType(migType);
        e.setRole(role.name());
        e.setBaseUrl(baseUrl.trim());
        e.setEdition(Edition.parse(edition).name());
        e.setUsername(username == null ? null : username.trim());
        if (password != null && !password.isEmpty()) {
            e.setPasswordEncrypted(crypto.encrypt(password));
        } else if (e.getPasswordEncrypted() == null) {
            throw new IllegalArgumentException("Password is required for a new connection");
        }
        e.setUseProxy(Boolean.TRUE.equals(useProxy));
        e.setLiveReportId(liveReportId);
        e.setUpdatedAt(LocalDateTime.now());
        return repository.save(e);
    }

    /** Builds an endpoint from form values without saving (for "Test connection"). */
    public OtcsEndpoint preview(String migType, Role role, String baseUrl, String edition, String username,
                                String password, Boolean useProxy) {
        String pwd = password;
        if (pwd == null || pwd.isEmpty()) {
            pwd = find(migType, role).map(c -> crypto.decrypt(c.getPasswordEncrypted()))
                    .orElseThrow(() -> new IllegalArgumentException("Enter the password to test the connection"));
        }
        return new OtcsEndpoint(migType + " " + role, baseUrl, Edition.parse(edition), username, pwd, Boolean.TRUE.equals(useProxy));
    }

    private OtcsEndpoint fromEntity(CsConnectionEntity e) {
        return new OtcsEndpoint(e.getMigType() + " " + e.getRole(), e.getBaseUrl(), Edition.parse(e.getEdition()),
                e.getUsername(), crypto.decrypt(e.getPasswordEncrypted()), Boolean.TRUE.equals(e.getUseProxy()));
    }

    /**
     * v1 keys. Target: cstargethostname / cstargetauthusername / cstargetauthpassword / csedition,
     * through the proxy unless "proxy=false". Source: cssource* keys, edition from cssourceedition
     * (default on-premise), no proxy unless "cssourceproxy=true".
     */
    private OtcsEndpoint fromSecrets(String migType, Role role) {
        boolean target = role == Role.TARGET;
        String prefix = target ? "cstarget" : "cssource";
        Edition edition = Edition.parse(target ? secrets.find("csedition").orElse("onprem")
                : secrets.find("cssourceedition").orElse("onprem"));
        String host = secrets.get(prefix + "hostname");
        String protocol = secrets.find(prefix + "protocol").orElse(props.getProtocol());
        String base = host.startsWith("http") ? host : protocol + host + edition.defaultContextPath;
        boolean proxy = target
                ? !"false".equalsIgnoreCase(secrets.find("proxy").orElse("true"))
                : "true".equalsIgnoreCase(secrets.find("cssourceproxy").orElse("false"));
        return new OtcsEndpoint(migType + " " + role, base, edition,
                secrets.get(prefix + "authusername"), secrets.get(prefix + "authpassword"), proxy);
    }
}
