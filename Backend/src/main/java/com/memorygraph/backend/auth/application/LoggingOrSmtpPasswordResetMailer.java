package com.memorygraph.backend.auth.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends the reset mail when SMTP is configured. Otherwise logs the URL so an operator can still
 * complete a tester's request without leaking whether the address exists (the caller already
 * filtered that).
 */
@Slf4j
@Component
public class LoggingOrSmtpPasswordResetMailer implements PasswordResetMailer {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final PasswordResetProperties properties;
    private final String mailHost;

    public LoggingOrSmtpPasswordResetMailer(
            ObjectProvider<JavaMailSender> mailSender,
            PasswordResetProperties properties,
            @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.mailHost = mailHost;
    }

    @Override
    public void sendResetLink(String email, String resetUrl) {
        JavaMailSender sender = mailConfigured() ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            log.info("Password reset for {}; SMTP is not configured. Operator reset link: {}", email, resetUrl);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFromOrDefault());
        message.setTo(email);
        message.setSubject("Reset your MemoryGraph password");
        message.setText("""
                Someone asked to reset the MemoryGraph password for this email.

                If that was you, open this link (it expires in one hour and works once):

                %s

                If you did not ask, you can ignore this message.
                """.formatted(resetUrl));
        try {
            sender.send(message);
            log.info("Sent password-reset email to {}", email);
        } catch (RuntimeException ex) {
            log.error("Failed to send password-reset email to {}", email, ex);
        }
    }

    private boolean mailConfigured() {
        return StringUtils.hasText(mailHost);
    }
}
