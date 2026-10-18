package com.migration;

import com.migration.tools.CategoryTools;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnitTests {

    @Test
    void categoryParseKeepsV1Format() {
        String raw = "12345;{30447,2}'ID'=2,'Values'={'2019-01-01'},'ID'=3,'Values'={{'abc'}}";
        String out = new CategoryTools().parse(raw);
        assertThat(out.trim()).isEqualTo("12345; \"cat_id\": \"30447\"; \"30447_2\": \"2019-01-01\"; \"30447_3\": \"abc\";");
    }

    @Test
    void categoryMapReplacesIds() {
        String mapping = "OldValue;NewValue\n\"cat_id\": \"30447\";\"cat_id\": \"999\"\n\"30447_2\";\"999_5\"\n";
        String input = "header\n12345; \"cat_id\": \"30447\"; \"30447_2\": \"2019-01-01\";\n";
        String out = new CategoryTools().map(mapping, input);
        assertThat(out).isEqualTo("DataId;Metadata\n12345;\"cat_id\": \"999\";\"999_5\":\"2019-01-01\"\n");
    }
}
