package com.migration.web;

import com.migration.core.MigrationTypes;
import com.migration.entities.CsConnectionEntity;
import com.migration.otcs.ConnectionService;
import com.migration.otcs.ConnectionService.Role;
import com.migration.otcs.OtcsClient;
import com.migration.otcs.OtcsEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.LinkedHashMap;
import java.util.Map;

/** Source and target Content Server per migration type. Passwords are stored encrypted and never returned. */
@RestController
@RequestMapping("/api/connections")
public class ConnectionApi {

    private final ConnectionService connections;
    private final OtcsClient otcs;
    private final MigrationTypes types;

    public ConnectionApi(ConnectionService connections, OtcsClient otcs, MigrationTypes types) {
        this.connections = connections;
        this.otcs = otcs;
        this.types = types;
    }

    @GetMapping("/{migType}")
    public Map<String, Object> get(@PathVariable String migType) {
        types.get(migType);
        Map<String, Object> m = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            m.put(role.name(), connections.find(migType, role).map(ConnectionApi::view).orElse(null));
        }
        return m;
    }

    public static class ConnectionForm {
        @NotBlank @Pattern(regexp = "https?://.+", message = "must start with http:// or https://")
        public String baseUrl;
        @NotBlank public String edition = "CLOUD";
        @NotBlank public String username;
        /** Empty = keep the stored one. */
        public String password;
        public Boolean useProxy = false;
        public Long liveReportId;
    }

    @PutMapping("/{migType}/{role}")
    public Map<String, Object> save(@PathVariable String migType, @PathVariable Role role, @Valid @RequestBody ConnectionForm f) {
        types.get(migType);
        return view(connections.save(migType, role, f.baseUrl, f.edition, f.username, f.password, f.useProxy, f.liveReportId));
    }

    /** Logs in with the form values (stored password if left empty) and, when given, reads the WebReport parameters. */
    @PostMapping("/{migType}/{role}/test")
    public Map<String, Object> test(@PathVariable String migType, @PathVariable Role role, @Valid @RequestBody ConnectionForm f) {
        OtcsEndpoint ep = connections.preview(migType, role, f.baseUrl, f.edition, f.username, f.password, f.useProxy);
        Map<String, Object> m = new LinkedHashMap<>();
        String error = otcs.test(ep);
        m.put("ok", error == null);
        m.put("message", error == null ? "Signed in to " + f.baseUrl + " as " + f.username : error);
        if (error == null && f.liveReportId != null) {
            m.put("reportParameters", otcs.reportParameters(ep, String.valueOf(f.liveReportId)));
        }
        return m;
    }

    private static Map<String, Object> view(CsConnectionEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baseUrl", c.getBaseUrl());
        m.put("edition", c.getEdition());
        m.put("username", c.getUsername());
        m.put("passwordSet", c.getPasswordEncrypted() != null);
        m.put("useProxy", c.getUseProxy());
        m.put("liveReportId", c.getLiveReportId());
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }
}
