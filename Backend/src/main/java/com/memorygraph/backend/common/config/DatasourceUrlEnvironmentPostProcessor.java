package com.memorygraph.backend.common.config;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Railway and Heroku supply {@code postgres://} / {@code postgresql://} URLs. JDBC and our
 * {@code application.yml} expect {@code jdbc:postgresql://}. Private Railway hosts also speak
 * plain TCP, not SSL.
 */
public class DatasourceUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE = "memorygraph-datasource";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean onRailway = environment.getProperty("RAILWAY_ENVIRONMENT") != null
                || environment.getProperty("RAILWAY_PROJECT_ID") != null;
        String raw = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("spring.datasource.url"));

        if (onRailway && (raw == null || raw.isBlank() || raw.contains("localhost"))) {
            throw new IllegalStateException(
                    "Backend on Railway needs a pgvector Postgres service named postgres. "
                            + "Set DATABASE_URL=jdbc:postgresql://postgres.railway.internal:5432/memorygraph "
                            + "(image pgvector/pgvector:pg17). Do not use Railway's default Postgres plugin.");
        }
        if (raw == null || raw.isBlank()) {
            return;
        }

        Parsed parsed = parse(raw);
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("spring.datasource.url", parsed.jdbcUrl());
        if (parsed.username() != null && !parsed.username().isBlank()) {
            overrides.put("spring.datasource.username", parsed.username());
        }
        if (parsed.password() != null) {
            overrides.put("spring.datasource.password", parsed.password());
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, overrides));
    }

    static Parsed parse(String raw) {
        String value = raw.trim();
        if (value.startsWith("postgres://") || value.startsWith("postgresql://")) {
            URI uri = URI.create(value);
            String userInfo = uri.getUserInfo();
            String username = null;
            String password = null;
            if (userInfo != null && !userInfo.isBlank()) {
                int colon = userInfo.indexOf(':');
                if (colon >= 0) {
                    username = userInfo.substring(0, colon);
                    password = userInfo.substring(colon + 1);
                } else {
                    username = userInfo;
                }
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/memorygraph" : uri.getPath();
            String query = uri.getQuery();
            String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + path;
            jdbc = withSslDisableForPrivateHost(jdbc, uri.getHost(), query);
            return new Parsed(jdbc, username, password);
        }

        String jdbc = value.startsWith("jdbc:") ? value : "jdbc:postgresql://" + value;
        String host = hostFromJdbc(jdbc);
        jdbc = withSslDisableForPrivateHost(jdbc, host, null);
        return new Parsed(jdbc, null, null);
    }

    private static String withSslDisableForPrivateHost(String jdbc, String host, String existingQuery) {
        String url = jdbc;
        if (existingQuery != null && !existingQuery.isBlank() && !url.contains("?")) {
            url = url + "?" + existingQuery;
        }
        boolean privateHost = host != null && host.endsWith(".railway.internal");
        if (!privateHost || url.contains("sslmode=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "sslmode=disable";
    }

    private static String hostFromJdbc(String jdbc) {
        try {
            String stripped = jdbc.startsWith("jdbc:") ? jdbc.substring(5) : jdbc;
            return URI.create(stripped).getHost();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    record Parsed(String jdbcUrl, String username, String password) {}
}
