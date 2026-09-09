package com.memorygraph.backend.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memorygraph.backend.ai.AiProperties;

@ExtendWith(MockitoExtension.class)
class AskQuotaServiceTest {

    private static final AiProperties CAPPED =
            new AiProperties("text-embedding-3-small", "gpt-4o-mini", 32, 5, 20, 0.0, 0.45);

    @Mock
    private AskGenerationUsageRepository usage;

    @Test
    void isUnlimitedWhenChatIsOff() {
        AskQuotaService quota = new AskQuotaService(usage, CAPPED, "none");
        UUID userId = UUID.randomUUID();

        assertThat(quota.hasAllowance(userId)).isTrue();
        assertThat(quota.status(userId).capped()).isFalse();
        assertThat(quota.consumeLanguageModelCall(userId)).isTrue();
    }

    @Test
    void reportsRemainingWhenChatIsOn() {
        AskQuotaService quota = new AskQuotaService(usage, CAPPED, "openai");
        UUID userId = UUID.randomUUID();
        when(usage.findByUserIdAndPeriodStart(any(), any())).thenReturn(Optional.empty());

        AskQuotaStatus status = quota.status(userId);
        assertThat(status.capped()).isTrue();
        assertThat(status.limit()).isEqualTo(20);
        assertThat(status.used()).isEqualTo(0);
        assertThat(status.remaining()).isEqualTo(20);
        assertThat(quota.hasAllowance(userId)).isTrue();
    }
}
