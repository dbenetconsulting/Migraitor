package com.migration.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the client's secrets file (key=value, same format as v1) into the Spring Environment,
 * so secrets can be read with {@code @Value("${key}")} or {@link Secrets#get(String)}.
 * The path comes from {@code management.credentials}. Secrets never get logged.
 */
public class SecretsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String SOURCE_NAME = "migrationSecrets";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String location = env.getProperty("management.credentials");
        if (location == null || location.isBlank()) {
            return;
        }
        Path path = Path.of(location.trim());
        if (!Files.isReadable(path)) {
            throw new IllegalStateException("Secrets file not readable: " + path);
        }
        env.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, read(path)));
    }

    static Map<String, Object> read(Path path) {
        Map<String, Object> secrets = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replace("﻿", "").trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                secrets.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading secrets file: " + path, e);
        }
        return secrets;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
