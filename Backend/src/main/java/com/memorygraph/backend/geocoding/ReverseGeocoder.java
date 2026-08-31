package com.memorygraph.backend.geocoding;

import java.util.Optional;

/**
 * Turns GPS into a short place label (e.g. "Gangtok, Sikkim"). Failures must be soft — callers keep
 * the coordinate fallback name.
 */
public interface ReverseGeocoder {

    Optional<String> resolveDisplayName(double latitude, double longitude);
}
