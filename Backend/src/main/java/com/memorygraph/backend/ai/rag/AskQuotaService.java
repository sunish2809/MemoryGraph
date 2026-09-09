package com.memorygraph.backend.ai.rag;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.ai.AiProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Caps how many times one account may call the language model for Ask in a UTC calendar month.
 * Retrieval-only answers do not count. Limit {@code 0} disables the cap (local tests, unlimited ops).
 */
@Slf4j
@Service
public class AskQuotaService {

    private final AskGenerationUsageRepository usage;
    private final AiProperties ai;
    private final boolean languageModelEnabled;

    public AskQuotaService(
            AskGenerationUsageRepository usage,
            AiProperties ai,
            @Value("${spring.ai.model.chat:none}") String chatProvider) {
        this.usage = usage;
        this.ai = ai;
        this.languageModelEnabled = "openai".equalsIgnoreCase(chatProvider);
    }

    public AskQuotaStatus status(UUID userId) {
        if (!capped()) {
            return AskQuotaStatus.unlimited();
        }
        int limit = ai.askMonthlyLimit();
        int used = usedThisPeriod(userId);
        return AskQuotaStatus.capped(limit, used, periodStart(), periodStart().plusMonths(1));
    }

    public boolean hasAllowance(UUID userId) {
        if (!capped()) {
            return true;
        }
        return usedThisPeriod(userId) < ai.askMonthlyLimit();
    }

    /**
     * Spends one language-model Ask. No-op and succeeds when the cap is off. Runs in its own
     * transaction because Ask itself is read-only.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consumeLanguageModelCall(UUID userId) {
        if (!capped()) {
            return true;
        }
        boolean spent = usage.tryIncrement(userId, periodStart(), ai.askMonthlyLimit()).isPresent();
        if (!spent) {
            log.info("Ask language-model cap reached for user {} ({} / {})",
                    userId, ai.askMonthlyLimit(), ai.askMonthlyLimit());
        }
        return spent;
    }

    private boolean capped() {
        return languageModelEnabled && ai.askMonthlyLimit() > 0;
    }

    private int usedThisPeriod(UUID userId) {
        return usage.findByUserIdAndPeriodStart(userId, periodStart())
                .map(AskGenerationUsage::getUsedCount)
                .orElse(0);
    }

    static LocalDate periodStart() {
        return YearMonth.now(ZoneOffset.UTC).atDay(1);
    }
}
