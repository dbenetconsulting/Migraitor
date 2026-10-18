package com.migration.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** A category to apply and its attribute values (attribute id -> value). */
public final class CategoryValues {

    public final String name;
    public final int categoryId;
    public final Map<Integer, String> attributes = new LinkedHashMap<>();

    public CategoryValues(String name, int categoryId) {
        this.name = name;
        this.categoryId = categoryId;
    }

    /** Adds the value; null or blank values are left out, as in v1. */
    public CategoryValues put(int attributeId, String value) {
        if (value != null && !value.isBlank()) attributes.put(attributeId, value);
        return this;
    }
}
