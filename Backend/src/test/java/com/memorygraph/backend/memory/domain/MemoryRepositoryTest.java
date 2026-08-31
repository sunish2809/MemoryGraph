package com.memorygraph.backend.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.support.TestcontainersConfiguration;
import com.memorygraph.backend.user.domain.User;
import com.memorygraph.backend.user.domain.UserRepository;

/**
 * Guards the product's most important security invariant: a memory is only ever reachable through
 * its owner. Also exercises the Flyway schema against the {@link Memory} mapping.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MemoryRepositoryTest {

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void aMemoryIsNotReachableThroughAnotherUser() {
        User owner = givenUser("owner");
        User stranger = givenUser("stranger");

        Memory memory = memoryRepository.save(
                Memory.create(owner, MemoryType.TEXT, MemorySource.MANUAL, Instant.now()));

        assertThat(memoryRepository.findByIdAndUserId(memory.getId(), owner.getId())).isPresent();
        assertThat(memoryRepository.findByIdAndUserId(memory.getId(), stranger.getId())).isEmpty();
        assertThat(memoryRepository.countByUserId(stranger.getId())).isZero();
    }

    @Test
    void timelineIsOrderedByWhenTheMemoryHappenedNotWhenItWasImported() {
        User owner = givenUser("timeline");
        Instant now = Instant.now();

        // Saved oldest-first so creation order cannot accidentally produce the expected ordering.
        Memory lastYear = save(owner, now.minus(365, ChronoUnit.DAYS), "Last year");
        Memory yesterday = save(owner, now.minus(1, ChronoUnit.DAYS), "Yesterday");
        Memory thisMorning = save(owner, now.minus(6, ChronoUnit.HOURS), "This morning");

        var timeline = memoryRepository.findTimelineIds(owner.getId(), PageRequest.of(0, 10));

        assertThat(timeline.getTotalElements()).isEqualTo(3);
        assertThat(timeline.getContent())
                .containsExactly(thisMorning.getId(), yesterday.getId(), lastYear.getId());
    }

    @Test
    void newMemoriesStartOutWaitingForProcessing() {
        User owner = givenUser("processing");

        Memory memory = save(owner, Instant.now(), "Note about Sikkim");

        assertThat(memory.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(memory.getCreatedAt()).isNotNull();
        assertThat(memory.getUserId()).isEqualTo(owner.getId());
    }

    private Memory save(User owner, Instant occurredAt, String title) {
        Memory memory = Memory.create(owner, MemoryType.TEXT, MemorySource.MANUAL, occurredAt);
        memory.describe(title, null, title);
        return memoryRepository.saveAndFlush(memory);
    }

    private User givenUser(String prefix) {
        return userRepository.saveAndFlush(User.register(
                prefix + "-" + System.nanoTime() + "@example.com",
                passwordEncoder.encode("correct-horse-battery"),
                "Test Person"));
    }
}
