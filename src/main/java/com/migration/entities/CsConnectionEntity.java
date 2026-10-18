package com.migration.entities;

import javax.persistence.*;
import java.time.LocalDateTime;

/** v2: Content Server connection per migration type and role (table cs_connection). */
@Entity
@Table(name = "cs_connection", uniqueConstraints = @UniqueConstraint(columnNames = {"migtype", "role"}))
public class CsConnectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "migtype", nullable = false, length = 40)
    private String migType;
    /** SOURCE or TARGET. */
    @Column(name = "role", nullable = false, length = 10)
    private String role;
    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;
    /** ONPREM or CLOUD. */
    @Column(name = "edition", nullable = false, length = 10)
    private String edition;
    @Column(name = "username", length = 200)
    private String username;
    /** AES-GCM encrypted with the configkey from the secrets file. */
    @Column(name = "password_enc", length = 1000)
    private String passwordEncrypted;
    @Column(name = "use_proxy")
    private Boolean useProxy;
    /** DataID of the LiveReport used to export documents (source) or folders (target). */
    @Column(name = "livereport_id")
    private Long liveReportId;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getMigType() { return migType; }
    public void setMigType(String migType) { this.migType = migType; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getEdition() { return edition; }
    public void setEdition(String edition) { this.edition = edition; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordEncrypted() { return passwordEncrypted; }
    public void setPasswordEncrypted(String passwordEncrypted) { this.passwordEncrypted = passwordEncrypted; }
    public Boolean getUseProxy() { return useProxy; }
    public void setUseProxy(Boolean useProxy) { this.useProxy = useProxy; }
    public Long getLiveReportId() { return liveReportId; }
    public void setLiveReportId(Long liveReportId) { this.liveReportId = liveReportId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
