package com.migration.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Access to values from the secrets file (and any other property source). */
@Component
public class Secrets {

    private final Environment env;

    public Secrets(Environment env) {
        this.env = env;
    }

    public String get(String key) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Secret not found for key: " + key);
        }
        return value.trim();
    }

    public Optional<String> find(String key) {
        String value = env.getProperty(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }
}
