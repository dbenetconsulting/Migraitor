package com.migration.core;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** All migration types found as Spring beans. Adding a type = adding a bean. */
@Component
public class MigrationTypes {

    private final Map<String, MigrationType<?>> byCode = new LinkedHashMap<>();

    public MigrationTypes(List<MigrationType<?>> types) {
        for (MigrationType<?> t : types) {
            if (byCode.put(t.code(), t) != null) {
                throw new IllegalStateException("Two migration types use the code " + t.code());
            }
        }
    }

    public Optional<MigrationType<?>> find(String code) {
        return Optional.ofNullable(code).map(byCode::get);
    }

    public MigrationType<?> get(String code) {
        return find(code).orElseThrow(() -> new IllegalArgumentException("Unknown migration type: " + code));
    }

    public Collection<MigrationType<?>> all() {
        return byCode.values();
    }
}
