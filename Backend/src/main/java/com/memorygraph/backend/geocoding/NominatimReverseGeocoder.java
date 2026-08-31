package com.memorygraph.backend.geocoding;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenStreetMap Nominatim reverse geocoding. Personal-use rate: at most one request per ~1.1s.
 * <p>
 * Parses JSON via {@link JsonMapper} (Jackson 3) so address maps bind reliably under Spring Boot 4.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "memorygraph.geocoding", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NominatimReverseGeocoder implements ReverseGeocoder {

    private static final long MIN_INTERVAL_MS = 1_100L;
    private static final List<String> LOCALITY_KEYS = List.of(
            "city", "town", "village", "municipality", "suburb", "hamlet", "city_district", "county");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RestClient http;
    private final Object lock = new Object();
    private long lastRequestAt;

    public NominatimReverseGeocoder(GeocodingProperties properties) {
        this.http = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.nominatimUrl()))
                .defaultHeader("User-Agent", properties.userAgent())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public Optional<String> resolveDisplayName(double latitude, double longitude) {
        throttle();
        try {
            String json = http.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("format", "json")
                            .queryParam("zoom", 14)
                            .queryParam("addressdetails", 1)
                            .build())
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return format(MAPPER.readTree(json));
        } catch (Exception ex) {
            log.warn("Nominatim reverse geocode failed for {},{}: {}", latitude, longitude, ex.getMessage());
            return Optional.empty();
        }
    }

    static Optional<String> format(JsonNode root) {
        if (root == null || root.isNull()) {
            return Optional.empty();
        }
        JsonNode address = root.path("address");
        if (address.isObject() && !address.isEmpty()) {
            String locality = LOCALITY_KEYS.stream()
                    .map(address::path)
                    .map(NominatimReverseGeocoder::textOrNull)
                    .filter(v -> v != null)
                    .findFirst()
                    .orElse(null);
            String state = firstNonBlank(textOrNull(address.path("state")), textOrNull(address.path("region")));
            String country = textOrNull(address.path("country"));

            if (locality != null) {
                if (state != null && !state.equalsIgnoreCase(locality)) {
                    return Optional.of(trimName(locality + ", " + state));
                }
                if (country != null && !country.equalsIgnoreCase(locality)) {
                    return Optional.of(trimName(locality + ", " + country));
                }
                return Optional.of(trimName(locality));
            }
            if (state != null) {
                if (country != null && !country.equalsIgnoreCase(state)) {
                    return Optional.of(trimName(state + ", " + country));
                }
                return Optional.of(trimName(state));
            }
            if (country != null) {
                return Optional.of(trimName(country));
            }
        }

        String displayName = textOrNull(root.path("display_name"));
        if (displayName != null) {
            return Optional.of(trimName(shortenDisplayName(displayName)));
        }
        return Optional.empty();
    }

    /** Keep the first two comma-separated parts of Nominatim's long display_name. */
    static String shortenDisplayName(String displayName) {
        String[] parts = displayName.split(",");
        if (parts.length >= 2) {
            return parts[0].strip() + ", " + parts[1].strip();
        }
        return displayName.strip();
    }

    private void throttle() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long wait = MIN_INTERVAL_MS - (now - lastRequestAt);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asString();
        return text == null || text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String trimName(String name) {
        String trimmed = name.strip();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
