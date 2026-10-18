package com.migration.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Builds the migration database connection from the secrets file, as in v1
 * (keys sqlserverurl/username/password or postgresqlurl/username/password).
 * Disabled when spring.datasource.url is set explicitly (tests, local runs).
 */
@Configuration
@ConditionalOnProperty(name = "spring.datasource.url", matchIfMissing = true, havingValue = "__secrets__")
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(MigrationProperties props, Secrets secrets) {
        boolean sqlServer = "sqlserver".equalsIgnoreCase(props.getDbType());
        String prefix = sqlServer ? "sqlserver" : "postgresql";
        return DataSourceBuilder.create()
                .url(secrets.get(prefix + "url"))
                .username(secrets.get(prefix + "username"))
                .password(secrets.get(prefix + "password"))
                .driverClassName(sqlServer ? "com.microsoft.sqlserver.jdbc.SQLServerDriver" : "org.postgresql.Driver")
                .build();
    }
}
