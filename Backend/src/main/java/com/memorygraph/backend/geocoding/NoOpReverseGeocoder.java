package com.memorygraph.backend.geocoding;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "memorygraph.geocoding", name = "enabled", havingValue = "false")
public class NoOpReverseGeocoder implements ReverseGeocoder {

    @Override
    public Optional<String> resolveDisplayName(double latitude, double longitude) {
        return Optional.empty();
    }
}
