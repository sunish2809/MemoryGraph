package com.memorygraph.backend.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class NominatimReverseGeocoderTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void formatsCityAndState() {
        Optional<String> name = NominatimReverseGeocoder.format(MAPPER.readTree("""
                {"address":{"city":"Gangtok","state":"Sikkim","country":"India"}}
                """));
        assertThat(name).contains("Gangtok, Sikkim");
    }

    @Test
    void fallsBackToCountry() {
        Optional<String> name = NominatimReverseGeocoder.format(MAPPER.readTree("""
                {"address":{"country":"Iceland"}}
                """));
        assertThat(name).contains("Iceland");
    }

    @Test
    void fallsBackToDisplayName() {
        Optional<String> name = NominatimReverseGeocoder.format(MAPPER.readTree("""
                {"display_name":"Sector V, Bidhannagar, West Bengal, India","address":{}}
                """));
        assertThat(name).contains("Sector V, Bidhannagar");
    }
}
