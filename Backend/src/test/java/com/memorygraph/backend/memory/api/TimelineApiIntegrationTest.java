package com.memorygraph.backend.memory.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.support.TestFixtures;
import com.memorygraph.backend.support.TestcontainersConfiguration;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimelineApiIntegrationTest {

    private static final String TIMELINE = ApiPaths.V1 + "/timeline";

    @Autowired
    private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        token = TestFixtures.registerAndAuthenticate(mockMvc);
    }

    @Test
    void groupsMemoriesIntoDaysNewestFirst() throws Exception {
        createTextMemory("Morning", "2024-03-10T08:00:00Z");
        createTextMemory("Evening", "2024-03-10T20:00:00Z");
        createTextMemory("Next week", "2024-03-17T09:00:00Z");

        mockMvc.perform(asOwner(get(TIMELINE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].date").value("2024-03-17"))
                .andExpect(jsonPath("$.data.days[0].memories.length()").value(1))
                .andExpect(jsonPath("$.data.days[1].date").value("2024-03-10"))
                .andExpect(jsonPath("$.data.days[1].memories.length()").value(2))
                // Within a day, newest first as well.
                .andExpect(jsonPath("$.data.days[1].memories[0].title").value("Evening"))
                .andExpect(jsonPath("$.data.totalItems").value(3));
    }

    /**
     * The reason grouping is a server concern rather than a display detail: the same instant belongs to
     * different calendar days depending on where the viewer is, and a late-evening memory would
     * otherwise appear on the wrong day.
     */
    @Test
    void groupsByTheViewersTimezoneRatherThanUtc() throws Exception {
        createTextMemory("Late night in Delhi", "2024-03-10T20:30:00Z");

        mockMvc.perform(asOwner(get(TIMELINE)).param("zone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].date").value("2024-03-10"));

        mockMvc.perform(asOwner(get(TIMELINE)).param("zone", "Asia/Kolkata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.zone").value("Asia/Kolkata"))
                // 20:30 UTC is past 2am the following day in Delhi.
                .andExpect(jsonPath("$.data.days[0].date").value("2024-03-11"));
    }

    @Test
    void restrictsToTheRequestedWindowInclusiveOfBothEnds() throws Exception {
        createTextMemory("Before", "2024-02-28T12:00:00Z");
        createTextMemory("First day", "2024-03-01T12:00:00Z");
        createTextMemory("Last day", "2024-03-31T23:00:00Z");
        createTextMemory("After", "2024-04-01T12:00:00Z");

        mockMvc.perform(asOwner(get(TIMELINE)).param("from", "2024-03-01").param("to", "2024-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.days[0].memories[0].title").value("Last day"))
                .andExpect(jsonPath("$.data.days[1].memories[0].title").value("First day"));
    }

    /** A childhood photo predates the Unix epoch, and must not be silently excluded. */
    @Test
    void includesMemoriesFromBeforeNineteenSeventy() throws Exception {
        createTextMemory("Grandmother's wedding", "1963-11-04T09:00:00Z");

        mockMvc.perform(asOwner(get(TIMELINE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.days[0].date").value("1963-11-04"));
    }

    @Test
    void rejectsAnUnknownTimezone() throws Exception {
        mockMvc.perform(asOwner(get(TIMELINE)).param("zone", "Mars/Olympus_Mons"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void showsNothingFromAnotherAccount() throws Exception {
        createTextMemory("Mine", "2024-03-10T08:00:00Z");
        String otherToken = TestFixtures.registerAndAuthenticate(mockMvc);

        mockMvc.perform(get(TIMELINE).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.days").isEmpty());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(TIMELINE)).andExpect(status().isUnauthorized());
    }

    private void createTextMemory(String title, String occurredAt) throws Exception {
        mockMvc.perform(asOwner(post(ApiPaths.V1 + "/memories/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"Content of %s","occurredAt":"%s"}
                                """.formatted(title, title, occurredAt)))
                .andExpect(status().isCreated());
    }

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T asOwner(T builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
