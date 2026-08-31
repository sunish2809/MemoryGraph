package com.memorygraph.backend.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

class ViewerZoneTest {

    @Test
    void acceptsARealIanaZone() {
        assertThat(ViewerZone.parse("Asia/Kolkata")).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void rejectsAnUnknownZoneAsAClientError() {
        assertThatThrownBy(() -> ViewerZone.parse("Mars/Olympus"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
