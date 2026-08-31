package com.memorygraph.backend.memory.application.imports.whatsapp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collapses a parsed chat into one transcript bucket per calendar day.
 */
final class WhatsAppDayBucketer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private WhatsAppDayBucketer() {
    }

    record DayBucket(LocalDate day, LocalDateTime firstMessageAt, String transcript,
            List<WhatsAppChatParser.WhatsAppMessage> messages) {
    }

    static List<DayBucket> bucket(List<WhatsAppChatParser.WhatsAppMessage> messages) {
        Map<LocalDate, List<WhatsAppChatParser.WhatsAppMessage>> byDay = new LinkedHashMap<>();
        for (WhatsAppChatParser.WhatsAppMessage message : messages) {
            byDay.computeIfAbsent(message.when().toLocalDate(), day -> new ArrayList<>()).add(message);
        }

        List<DayBucket> buckets = new ArrayList<>();
        for (Map.Entry<LocalDate, List<WhatsAppChatParser.WhatsAppMessage>> entry : byDay.entrySet()) {
            List<WhatsAppChatParser.WhatsAppMessage> dayMessages = entry.getValue();
            StringBuilder transcript = new StringBuilder();
            for (WhatsAppChatParser.WhatsAppMessage message : dayMessages) {
                if (!transcript.isEmpty()) {
                    transcript.append('\n');
                }
                transcript.append('[')
                        .append(TIME.format(message.when()))
                        .append("] ")
                        .append(message.sender())
                        .append(": ")
                        .append(message.body());
            }
            buckets.add(new DayBucket(entry.getKey(), dayMessages.getFirst().when(), transcript.toString(),
                    List.copyOf(dayMessages)));
        }
        return List.copyOf(buckets);
    }

    static String title(String chatName, LocalDate day) {
        return "WhatsApp · " + chatName + " · " + DAY.format(day);
    }

    static java.time.Instant occurredAt(LocalDateTime local, ZoneId zone) {
        return local.atZone(zone).toInstant();
    }
}
