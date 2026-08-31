package com.memorygraph.backend.memory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.memory.search.SearchHighlight;
import com.memorygraph.backend.support.TestFixtures;
import com.memorygraph.backend.support.TestcontainersConfiguration;

/**
 * Search end to end.
 * <p>
 * Two things get the most attention here. The first is that search never leaks: a query is the easiest
 * way to accidentally reach across accounts, because unlike fetching a memory by id there is no
 * identifier for an owner check to hang off. The second is that no text a person could type — including
 * text that looks like query syntax — can make the endpoint fail, since the search box accepts anything.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SearchApiIntegrationTest {

    private static final String SEARCH = ApiPaths.V1 + "/search";
    private static final String MEMORIES = ApiPaths.V1 + "/memories";

    @Autowired
    private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        token = TestFixtures.registerAndAuthenticate(mockMvc);
    }

    @Test
    void findsAMemoryByAWordInItsBody() throws Exception {
        createNote("Sikkim trip", "The train to Gangtok was delayed by four hours.", "2019-04-02T09:00:00Z");
        createNote("Tax return", "Filed the paperwork and kept the receipts.", "2019-07-11T09:00:00Z");

        search("gangtok")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].memory.title").value("Sikkim trip"));
    }

    /**
     * The reason the query is built as a prefix match: someone searching their own archive types a few
     * letters and expects to see the thing appear, not to have to guess the whole word.
     */
    @Test
    void findsAPartialWordWhileTheUserIsStillTyping() throws Exception {
        createNote("Birthday party", "Cake in the garden.", "2021-08-14T09:00:00Z");

        search("birth")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].memory.title").value("Birthday party"));
    }

    /** Stemming, not prefix matching: "runs" and "running" share a stem but neither contains the other. */
    @Test
    void matchesADifferentGrammaticalFormOfTheSameWord() throws Exception {
        createNote("Late again", "We were running for the last bus.", "2022-02-02T09:00:00Z");

        search("runs")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].memory.title").value("Late again"));
    }

    @Test
    void treatsQuotedTextAsAnExactPhrase() throws Exception {
        createNote("Birthday party", "Cake in the garden.", "2021-08-14T09:00:00Z");

        search("\"birthday party\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1));

        // The same two words in the wrong order are no longer the phrase that was asked for.
        search("\"party birthday\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    void ranksATitleMatchAboveAPassingMentionInABody() throws Exception {
        createNote("A long day", "We walked past the monastery on the way back to the hotel.",
                "2019-04-03T09:00:00Z");
        createNote("Monastery", "Notes from the visit.", "2019-04-04T09:00:00Z");

        search("monastery")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[0].memory.title").value("Monastery"))
                .andExpect(jsonPath("$.data.items[1].memory.title").value("A long day"))
                .andExpect(jsonPath("$.data.items[0].snippet",
                        containsString(SearchHighlight.START + "Monastery" + SearchHighlight.END)));
    }

    @Test
    void marksTheMatchedWordsInsideTheSnippet() throws Exception {
        createNote("A long day", "We walked past the monastery on the way back.", "2019-04-03T09:00:00Z");

        search("monastery")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].snippet",
                        containsString(SearchHighlight.START + "monastery" + SearchHighlight.END)));
    }

    /**
     * A photo's filename is folded into its searchable text by the processing pipeline, and is often the
     * only words it has.
     */
    @Test
    void findsAPhotoByItsFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "gangtok-monastery.png", "image/png",
                TestFixtures.pngImage(48, 48));
        String memoryId = JsonPath.read(mockMvc.perform(asOwner(multipart(MEMORIES + "/upload").file(file)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        // Searchable text is written by the enrichment step, so the memory is not findable until it runs.
        TestFixtures.awaitProcessed(mockMvc, token, memoryId);

        search("gangtok")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].memory.type").value("PHOTO"));
    }

    @Test
    void filtersByMemoryType() throws Exception {
        createNote("Written down", "A note about the harbour.", "2023-01-01T09:00:00Z");
        MockMultipartFile file = new MockMultipartFile("file", "harbour.png", "image/png",
                TestFixtures.pngImage(24, 24));
        mockMvc.perform(asOwner(multipart(MEMORIES + "/upload").file(file)).param("title", "Harbour at dusk"))
                .andExpect(status().isCreated());

        mockMvc.perform(asOwner(get(SEARCH)).param("q", "harbour").param("type", "PHOTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].memory.type").value("PHOTO"));

        mockMvc.perform(asOwner(get(SEARCH)).param("q", "harbour").param("type", "TEXT").param("type", "PHOTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2));
    }

    /**
     * The date filter means the viewer's calendar day, not the server's. This memory falls on 1 March in
     * UTC and 2 March in Kolkata, and both answers have to be right in their own zone.
     */
    @Test
    void resolvesTheDateFilterInTheRequestedTimezone() throws Exception {
        createNote("Late night", "Something happened just before bed.", "2024-03-01T20:00:00Z");

        mockMvc.perform(asOwner(get(SEARCH)).param("from", "2024-03-01").param("to", "2024-03-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1));

        mockMvc.perform(asOwner(get(SEARCH))
                        .param("from", "2024-03-02").param("to", "2024-03-02").param("zone", "Asia/Kolkata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1));

        mockMvc.perform(asOwner(get(SEARCH))
                        .param("from", "2024-03-01").param("to", "2024-03-01").param("zone", "Asia/Kolkata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    void excludesMemoriesOutsideTheDateRange() throws Exception {
        createNote("Before", "A note.", "2018-01-01T09:00:00Z");
        createNote("Inside", "A note.", "2019-06-01T09:00:00Z");
        createNote("After", "A note.", "2020-01-01T09:00:00Z");

        mockMvc.perform(asOwner(get(SEARCH)).param("from", "2019-01-01").param("to", "2019-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].memory.title").value("Inside"));
    }

    /**
     * With no text, search becomes a filtered browse. There is nothing to rank, so it falls back to
     * newest-first, and nothing to highlight, so the snippet is absent rather than a fabricated one.
     */
    @Test
    void browsesByFilterAloneWhenNoQueryIsGiven() throws Exception {
        createNote("Older", "A note.", "2020-01-01T09:00:00Z");
        createNote("Newer", "A note.", "2022-01-01T09:00:00Z");

        mockMvc.perform(asOwner(get(SEARCH)).param("type", "TEXT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[0].memory.title").value("Newer"))
                .andExpect(jsonPath("$.data.items[0].snippet").doesNotExist())
                // The excerpt is still there, which is what a client shows instead.
                .andExpect(jsonPath("$.data.items[0].memory.excerpt").value("A note."));
    }

    /** Asking for relevance with nothing to rank is the client clearing the search box, not an error. */
    @Test
    void quietlyOrdersByDateWhenRelevanceIsAskedForWithoutAQuery() throws Exception {
        createNote("Older", "A note.", "2020-01-01T09:00:00Z");
        createNote("Newer", "A note.", "2022-01-01T09:00:00Z");

        mockMvc.perform(asOwner(get(SEARCH)).param("sort", "RELEVANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].memory.title").value("Newer"));
    }

    @Test
    void sortsOldestFirstWhenAsked() throws Exception {
        createNote("First mention", "We talked about Sikkim.", "2018-01-01T09:00:00Z");
        createNote("Later mention", "Sikkim again.", "2021-01-01T09:00:00Z");

        mockMvc.perform(asOwner(get(SEARCH)).param("q", "sikkim").param("sort", "OLDEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].memory.title").value("First mention"));
    }

    @Test
    void paginatesWithoutRepeatingOrLosingResults() throws Exception {
        createNote("Trip one", "A trip to the hills.", "2019-01-01T09:00:00Z");
        createNote("Trip two", "A trip to the coast.", "2020-01-01T09:00:00Z");
        createNote("Trip three", "A trip to the desert.", "2021-01-01T09:00:00Z");

        String first = mockMvc.perform(asOwner(get(SEARCH))
                        .param("q", "trip").param("sort", "NEWEST").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(asOwner(get(SEARCH))
                        .param("q", "trip").param("sort", "NEWEST").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andReturn().getResponse().getContentAsString();

        List<String> firstPage = JsonPath.read(first, "$.data.items[*].memory.id");
        List<String> secondPage = JsonPath.read(second, "$.data.items[*].memory.id");
        assertThat(firstPage).hasSize(2).doesNotContainAnyElementsOf(secondPage);
    }

    /**
     * Text with no searchable words matches nothing rather than everything. Returning the whole archive
     * because someone typed "the" would look like the filter had been ignored.
     */
    @ParameterizedTest
    @ValueSource(strings = { "the", "a", "of and or", "...", "!!!" })
    void findsNothingForTextWithNoSearchableWords(String query) throws Exception {
        createNote("Something", "A note about the harbour.", "2023-01-01T09:00:00Z");

        search(query)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    /**
     * A search box accepts whatever is typed into it, so none of this may reach the query parser as
     * syntax. Each of these once had the potential to produce a tsquery parse error, which would have
     * surfaced as a 500 from an ordinary keystroke.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "& | ! ( ) <-> :*",
            "'; DROP TABLE memories; --",
            "<script>alert(1)</script>",
            "foo:*&bar|!baz",
            "\"unbalanced",
            "it's",
            "e-mail someone@example.com",
            "https://example.com/a?b=c&d=e",
            "café naïve 日本語 🎂",
            "3.14 -7 0x1f",
            "\\\\ \\x00",
    })
    void survivesAnyTextAPersonCouldType(String query) throws Exception {
        createNote("Something", "A note about the harbour.", "2023-01-01T09:00:00Z");

        search(query).andExpect(status().isOk());
    }

    @Test
    void neverReturnsAnotherAccountsMemories() throws Exception {
        createNote("Sikkim trip", "The train to Gangtok was delayed.", "2019-04-02T09:00:00Z");

        String otherToken = TestFixtures.registerAndAuthenticate(mockMvc);

        mockMvc.perform(get(SEARCH).param("q", "gangtok")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void neverReturnsAnotherAccountsMemoriesWhenBrowsingByFilter() throws Exception {
        createNote("Private note", "Something personal.", "2019-04-02T09:00:00Z");

        String otherToken = TestFixtures.registerAndAuthenticate(mockMvc);

        mockMvc.perform(get(SEARCH).param("type", "TEXT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    void rejectsAnUnknownMemoryType() throws Exception {
        mockMvc.perform(asOwner(get(SEARCH)).param("type", "TELEPATHY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors.type").exists());
    }

    @Test
    void rejectsAnUnknownSortOrder() throws Exception {
        mockMvc.perform(asOwner(get(SEARCH)).param("sort", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsADateThatIsNotADate() throws Exception {
        mockMvc.perform(asOwner(get(SEARCH)).param("from", "last-tuesday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors.from").exists());
    }

    @Test
    void rejectsAnUnknownTimezone() throws Exception {
        mockMvc.perform(asOwner(get(SEARCH)).param("zone", "Mars/Olympus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsAQueryLongerThanAnyRealSearch() throws Exception {
        mockMvc.perform(asOwner(get(SEARCH)).param("q", "x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsAPageSizeBeyondTheLimit() throws Exception {
        mockMvc.perform(asOwner(get(SEARCH)).param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(SEARCH).param("q", "anything"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions search(String query) throws Exception {
        return mockMvc.perform(asOwner(get(SEARCH)).param("q", query));
    }

    private void createNote(String title, String content, String occurredAt) throws Exception {
        mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"%s","occurredAt":"%s"}
                                """.formatted(title, content, occurredAt)))
                .andExpect(status().isCreated());
    }

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T asOwner(T builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
