package com.memorygraph.backend.integration.google;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GooglePhotosPickerClient {

    private static final int PAGE_SIZE = 100;
    public static final int MAX_ITEMS = 500;

    private final RestClient picker;
    private final RestClient media;

    public GooglePhotosPickerClient() {
        JdkClientHttpRequestFactory mediaFactory = new JdkClientHttpRequestFactory();
        mediaFactory.setReadTimeout(Duration.ofMinutes(3));
        this.picker = RestClient.builder()
                .baseUrl("https://photospicker.googleapis.com")
                .build();
        this.media = RestClient.builder()
                .requestFactory(mediaFactory)
                .build();
    }

    public PickerSession createSession(String accessToken) {
        try {
            PickerSession session = picker.post()
                    .uri("/v1/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(PickerSession.class);
            if (session == null || session.id() == null || session.pickerUri() == null) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Google Photos picker session was empty");
            }
            return session;
        } catch (RestClientException ex) {
            log.warn("Picker session create failed: {}", ex.getMessage());
            String detail = ex.getMessage() != null ? ex.getMessage() : "";
            if (detail.contains("SERVICE_DISABLED") || detail.contains("has not been used")
                    || detail.contains("is disabled")) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Enable the Google Photos Picker API in Google Cloud for this project, wait a few minutes, then try again. "
                                + "https://console.developers.google.com/apis/api/photospicker.googleapis.com/overview",
                        ex);
            }
            if (detail.contains("403") || detail.contains("PERMISSION_DENIED")) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Google Photos Picker was denied. Check the Photos Picker API is enabled and your OAuth consent includes the picker scope.",
                        ex);
            }
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not start Google Photos picker", ex);
        }
    }

    public PickerSession getSession(String accessToken, String sessionId) {
        try {
            PickerSession session = picker.get()
                    .uri("/v1/sessions/{id}", sessionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(PickerSession.class);
            if (session == null) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Picker session not found");
            }
            return session;
        } catch (RestClientException ex) {
            log.warn("Picker session get failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not poll Google Photos picker", ex);
        }
    }

    public List<PickedMediaItem> listAllMediaItems(String accessToken, String sessionId) {
        List<PickedMediaItem> items = new ArrayList<>();
        String pageToken = null;
        do {
            final String token = pageToken;
            MediaItemsPage page;
            try {
                page = picker.get()
                        .uri(uriBuilder -> {
                            var b = uriBuilder.path("/v1/mediaItems")
                                    .queryParam("sessionId", sessionId)
                                    .queryParam("pageSize", PAGE_SIZE);
                            if (token != null) {
                                b.queryParam("pageToken", token);
                            }
                            return b.build();
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .body(MediaItemsPage.class);
            } catch (RestClientException ex) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not list picked photos", ex);
            }
            if (page == null || page.mediaItems() == null) {
                break;
            }
            for (PickedMediaItem item : page.mediaItems()) {
                items.add(item);
                if (items.size() >= MAX_ITEMS) {
                    return items;
                }
            }
            pageToken = page.nextPageToken() != null && !page.nextPageToken().isBlank()
                    ? page.nextPageToken()
                    : null;
        } while (pageToken != null);
        return items;
    }

    /**
     * Downloads raw bytes from a picker {@code baseUrl}. Uses {@link URI#create(String)} so Spring
     * does not re-encode Google's {@code =d}/{@code =dv} suffixes (a common RestClient pitfall).
     */
    public byte[] downloadBytes(String accessToken, String baseUrl, boolean video) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Picked media has no download URL");
        }
        String primary = withDownloadParam(baseUrl, video);
        try {
            return fetch(accessToken, primary);
        } catch (RestClientException primaryEx) {
            log.warn("Primary download failed ({}): {}", primary, rootMessage(primaryEx));
            if (!video) {
                String fallback = withDownloadParam(baseUrl, false, true);
                if (!fallback.equals(primary)) {
                    try {
                        return fetch(accessToken, fallback);
                    } catch (RestClientException fallbackEx) {
                        log.warn("Fallback download failed ({}): {}", fallback, rootMessage(fallbackEx));
                    }
                }
            }
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Could not download picked media: " + rootMessage(primaryEx), primaryEx);
        }
    }

    private byte[] fetch(String accessToken, String url) {
        byte[] bytes = media.get()
                .uri(URI.create(url))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Downloaded media was empty");
        }
        // Google sometimes returns an HTML/JSON error body with HTTP 200; reject tiny non-media.
        if (bytes.length < 32 && looksLikeText(bytes)) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Download returned an error payload instead of media bytes");
        }
        return bytes;
    }

    private static String withDownloadParam(String baseUrl, boolean video) {
        return withDownloadParam(baseUrl, video, false);
    }

    private static String withDownloadParam(String baseUrl, boolean video, boolean sizedFallback) {
        if (baseUrl.contains("=")) {
            return baseUrl;
        }
        if (video) {
            return baseUrl + "=dv";
        }
        return sizedFallback ? baseUrl + "=w4096-h4096" : baseUrl + "=d";
    }

    private static String rootMessage(Throwable ex) {
        if (ex instanceof RestClientResponseException response) {
            return response.getStatusCode() + " " + response.getStatusText()
                    + (response.getResponseBodyAsString().isBlank()
                            ? ""
                            : ": " + truncate(response.getResponseBodyAsString(), 200));
        }
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static boolean looksLikeText(byte[] bytes) {
        for (byte b : bytes) {
            if (b == '{' || b == '<' || b == '[') {
                return true;
            }
        }
        return false;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PickerSession(
            String id,
            String pickerUri,
            Boolean mediaItemsSet,
            PollingConfig pollingConfig) {

        public boolean selectionComplete() {
            return Boolean.TRUE.equals(mediaItemsSet);
        }

        public long pollIntervalMs() {
            if (pollingConfig == null || pollingConfig.pollInterval() == null) {
                return 3_000L;
            }
            return parseDurationMs(pollingConfig.pollInterval(), 3_000L);
        }

        private static long parseDurationMs(String duration, long fallback) {
            String trimmed = duration.trim();
            if (trimmed.endsWith("s")) {
                try {
                    double seconds = Double.parseDouble(trimmed.substring(0, trimmed.length() - 1));
                    return Math.max(1_000L, (long) (seconds * 1000));
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PollingConfig(
            @JsonProperty("pollInterval") String pollInterval,
            @JsonProperty("timeoutIn") String timeoutIn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaItemsPage(
            List<PickedMediaItem> mediaItems,
            String nextPageToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PickedMediaItem(
            String id,
            String createTime,
            String type,
            MediaFile mediaFile) {

        public boolean isVideo() {
            if ("VIDEO".equalsIgnoreCase(type)) {
                return true;
            }
            String mime = mimeType();
            return mime != null && mime.toLowerCase().startsWith("video/");
        }

        public String baseUrl() {
            return mediaFile != null ? mediaFile.baseUrl() : null;
        }

        public String mimeType() {
            return mediaFile != null ? mediaFile.mimeType() : null;
        }

        public String filename() {
            return mediaFile != null ? mediaFile.filename() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaFile(String baseUrl, String mimeType, String filename) {
    }
}
