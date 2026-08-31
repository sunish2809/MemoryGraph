package com.memorygraph.backend.memory.application.imports.whatsapp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Parses a WhatsApp {@code _chat.txt} export into timestamped messages.
 * <p>
 * Formats vary by OS and locale: iOS usually uses {@code [date, time] Name: …}; Android often uses
 * {@code date, time - Name: …}. Date separators may be {@code /}, {@code .} or {@code -}; times may
 * use {@code :} or {@code .}; lines often start with invisible LTR marks. Unrecognised continuation
 * lines append to the previous message.
 */
@Component
public class WhatsAppChatParser {

    /**
     * Invisible marks WhatsApp inserts freely. They break {@code ^\\[} anchors if left in place.
     */
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200e\\u200f\\u202a-\\u202e\\ufeff\\u00a0\\u202f]");

    /**
     * Date + time capture used inside both bracketed and dashed headers. Date separator is /, . or -;
     * comma between date and time is optional; AM/PM may include dots ({@code a.m.}).
     */
    private static final String DATE_TIME =
            "(\\d{1,4}[/.-]\\d{1,2}[/.-]\\d{1,4})[,\\s]+(\\d{1,2}[:.]\\d{2}(?:[:.]\\d{2})?(?:\\s*[AaPp]\\.?\\s*[Mm]\\.?)?)";

    private static final Pattern BRACKETED = Pattern.compile("^\\[" + DATE_TIME + "\\]\\s*(.+)$");

    private static final Pattern DASHED = Pattern.compile("^" + DATE_TIME + "\\s+-\\s+(.+)$");

    private static final Pattern SENDER_BODY = Pattern.compile("^([^:]+):\\s?(.*)$", Pattern.DOTALL);

    private static final Pattern ATTACHED = Pattern.compile(
            "<attached:\\s*([^>]+)>|([^\\s]+\\.(?:jpg|jpeg|png|gif|webp|heic|mp4|opus|pdf))\\s*\\(file attached\\)",
            Pattern.CASE_INSENSITIVE);

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            formatter("d/M/uuuu"),
            formatter("d/M/uu"),
            formatter("M/d/uuuu"),
            formatter("M/d/uu"),
            formatter("uuuu/M/d"),
            formatter("d.M.uuuu"),
            formatter("d.M.uu"),
            formatter("M.d.uuuu"),
            formatter("M.d.uu"),
            formatter("uuuu.M.d"),
            formatter("d-M-uuuu"),
            formatter("d-M-uu"),
            formatter("M-d-uuuu"),
            formatter("M-d-uu"),
            formatter("uuuu-M-d"));

    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            formatter("H:mm:ss"),
            formatter("H:mm"),
            formatter("h:mm:ss a"),
            formatter("h:mm a"),
            formatter("h:mm:ssa"),
            formatter("h:mma"),
            formatter("H.mm.ss"),
            formatter("H.mm"),
            formatter("h.mm.ss a"),
            formatter("h.mm a"));

    public ParsedChat parse(String chatText, String preferredChatName) {
        List<WhatsAppMessage> messages = new ArrayList<>();
        WhatsAppMessage current = null;

        for (String rawLine : chatText.split("\\R", -1)) {
            String line = sanitiseLine(rawLine);
            Optional<Header> header = parseHeader(line);
            if (header.isPresent()) {
                Header h = header.get();
                if (h.system()) {
                    current = null;
                    continue;
                }
                current = new WhatsAppMessage(h.when(), h.sender(), h.body(), extractAttachments(h.body()));
                messages.add(current);
            } else if (current != null && StringUtils.hasText(line)) {
                String extended = current.body() + "\n" + line;
                messages.set(messages.size() - 1,
                        new WhatsAppMessage(current.when(), current.sender(), extended, extractAttachments(extended)));
                current = messages.get(messages.size() - 1);
            }
        }

        String chatName = StringUtils.hasText(preferredChatName) ? preferredChatName.strip() : "WhatsApp chat";
        return new ParsedChat(chatName, List.copyOf(messages));
    }

    /**
     * First few non-blank lines after sanitising — useful in error messages when nothing parsed.
     */
    public static String previewLines(String chatText, int maxLines) {
        List<String> lines = new ArrayList<>();
        for (String raw : chatText.split("\\R", -1)) {
            String line = sanitiseLine(raw);
            if (!StringUtils.hasText(line)) {
                continue;
            }
            lines.add(line.length() > 120 ? line.substring(0, 117) + "…" : line);
            if (lines.size() >= maxLines) {
                break;
            }
        }
        return lines.isEmpty() ? "(empty file)" : String.join(" | ", lines);
    }

    /**
     * Derives a display name from a WhatsApp export filename such as
     * {@code WhatsApp Chat with Rahul.txt} or {@code WhatsApp Chat with Rahul.zip}.
     */
    public static String chatNameFromFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "WhatsApp chat";
        }
        String base = fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String lowered = base.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("whatsapp chat with ")) {
            return base.substring("WhatsApp Chat with ".length()).strip();
        }
        if (lowered.equals("_chat") || lowered.equals("chat")) {
            return "WhatsApp chat";
        }
        return base.strip();
    }

    static String sanitiseLine(String raw) {
        if (raw == null) {
            return "";
        }
        return INVISIBLE.matcher(raw).replaceAll("").strip();
    }

    private Optional<Header> parseHeader(String line) {
        if (!StringUtils.hasText(line)) {
            return Optional.empty();
        }
        Matcher bracketed = BRACKETED.matcher(line);
        if (bracketed.matches()) {
            return headerFrom(bracketed.group(1), bracketed.group(2), bracketed.group(3));
        }
        Matcher dashed = DASHED.matcher(line);
        if (dashed.matches()) {
            return headerFrom(dashed.group(1), dashed.group(2), dashed.group(3));
        }
        return Optional.empty();
    }

    private Optional<Header> headerFrom(String date, String time, String rest) {
        LocalDateTime when = parseDateTime(date, time).orElse(null);
        if (when == null) {
            return Optional.empty();
        }
        Matcher senderBody = SENDER_BODY.matcher(rest);
        if (!senderBody.matches()) {
            return Optional.of(new Header(when, null, rest, true));
        }
        String sender = senderBody.group(1).strip();
        String body = senderBody.group(2);
        if (!StringUtils.hasText(sender)) {
            return Optional.of(new Header(when, null, rest, true));
        }
        return Optional.of(new Header(when, sender, body == null ? "" : body, false));
    }

    private Optional<LocalDateTime> parseDateTime(String date, String time) {
        // Normalise separators so one set of formatters covers /, . and -.
        String normalisedDate = date.strip().replace('-', '/').replace('.', '/');
        LocalDate localDate = null;
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                localDate = LocalDate.parse(normalisedDate, formatter);
                break;
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        if (localDate == null) {
            return Optional.empty();
        }

        String normalisedTime = time.strip()
                .replace('\u202F', ' ')
                .replace('\u00A0', ' ')
                .replaceAll("(?i)\\s*a\\.?\\s*m\\.?", " AM")
                .replaceAll("(?i)\\s*p\\.?\\s*m\\.?", " PM")
                .replace('.', ':')
                .replaceAll("\\s+", " ")
                .strip()
                .toUpperCase(Locale.ROOT);
        for (DateTimeFormatter formatter : TIME_FORMATS) {
            try {
                LocalTime localTime = LocalTime.parse(normalisedTime, formatter);
                return Optional.of(LocalDateTime.of(localDate, localTime));
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return Optional.empty();
    }

    private static List<String> extractAttachments(String body) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        Matcher matcher = ATTACHED.matcher(body);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (StringUtils.hasText(name)) {
                names.add(name.strip());
            }
        }
        return List.copyOf(names);
    }

    private static DateTimeFormatter formatter(String pattern) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.SMART);
    }

    private record Header(LocalDateTime when, String sender, String body, boolean system) {
    }

    public record ParsedChat(String chatName, List<WhatsAppMessage> messages) {
    }

    public record WhatsAppMessage(LocalDateTime when, String sender, String body, List<String> attachments) {
    }
}
