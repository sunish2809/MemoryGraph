package com.memorygraph.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatasourceUrlEnvironmentPostProcessorTest {

    @Test
    void convertsPostgresUriIntoJdbc() {
        var parsed = DatasourceUrlEnvironmentPostProcessor.parse(
                "postgresql://memorygraph:secret@postgres.railway.internal:5432/memorygraph");
        assertThat(parsed.jdbcUrl())
                .isEqualTo("jdbc:postgresql://postgres.railway.internal:5432/memorygraph?sslmode=disable");
        assertThat(parsed.username()).isEqualTo("memorygraph");
        assertThat(parsed.password()).isEqualTo("secret");
    }

    @Test
    void leavesLocalJdbcAlone() {
        var parsed = DatasourceUrlEnvironmentPostProcessor.parse(
                "jdbc:postgresql://db:5432/memorygraph");
        assertThat(parsed.jdbcUrl()).isEqualTo("jdbc:postgresql://db:5432/memorygraph");
        assertThat(parsed.username()).isNull();
    }
}
