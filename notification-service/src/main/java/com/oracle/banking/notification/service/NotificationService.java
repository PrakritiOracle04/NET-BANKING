package com.oracle.banking.notification.service;

import com.oracle.banking.notification.NotificationStatus;
import com.oracle.banking.notification.dto.NotificationDtos.EmailRequest;
import com.oracle.banking.notification.dto.NotificationDtos.EmailResponse;
import com.oracle.banking.notification.dto.NotificationDtos.EmailSummaryResponse;
import com.oracle.banking.notification.entity.EmailDeliveryLog;
import com.oracle.banking.notification.entity.EmailNotification;
import com.oracle.banking.notification.entity.EmailTemplate;
import com.oracle.banking.notification.repository.EmailDeliveryLogRepository;
import com.oracle.banking.notification.repository.EmailNotificationRepository;
import com.oracle.banking.notification.repository.EmailTemplateRepository;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\{\\{([A-Za-z0-9_.-]+)}}");

    private final EmailNotificationRepository notifications;
    private final EmailTemplateRepository templates;
    private final EmailDeliveryLogRepository deliveryLogs;
    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final String senderName;
    private final int maxRetries;

    public NotificationService(
            EmailNotificationRepository notifications,
            EmailTemplateRepository templates,
            EmailDeliveryLogRepository deliveryLogs,
            JavaMailSender mailSender,
            @Value("${notification.sender-email}") String senderEmail,
            @Value("${notification.sender-name}") String senderName,
            @Value("${notification.max-retries}") int maxRetries) {
        this.notifications = notifications;
        this.templates = templates;
        this.deliveryLogs = deliveryLogs;
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
        this.maxRetries = maxRetries;
    }

    @PostConstruct
    void createDefaultTemplates() {
        seedTemplate(
                "WELCOME",
                "WELCOME",
                "Welcome to Oracle Banking, {{customerName}}",
                "<h2>Welcome, {{customerName}}</h2>"
                        + "<p>Your Oracle Banking profile is ready.</p>",
                "Welcome, {{customerName}}. Your Oracle Banking profile is ready.");
        seedTemplate(
                "LOGIN_ALERT",
                "LOGIN_ALERT",
                "New login to your Oracle Banking account",
                "<h2>Login alert</h2>"
                        + "<p>Hello {{customerName}}, "
                        + "a login was detected at {{currentTime}}.</p>",
                "Hello {{customerName}}, a login was detected at {{currentTime}}.");
        seedTemplate(
                "PASSWORD_RESET",
                "PASSWORD_RESET",
                "Reset your Oracle Banking password",
                "<h2>Password reset</h2>"
                        + "<p>Use this link: {{verificationLink}}</p>",
                "Reset link: {{verificationLink}}");
        seedTemplate(
                "GENERIC_NOTIFICATION",
                "GENERIC",
                "Oracle Banking notification",
                "<p>{{message}}</p>",
                "{{message}}");
        seedTemplate(
                "LOAN_CREATED",
                "LOAN",
                "Your Oracle Banking loan {{loanNumber}} is active",
                "<h2>Loan created</h2>"
                        + "<p>Your loan {{loanNumber}} for {{principalAmount}} is now active.</p>"
                        + "<p>EMI: {{emiAmount}} &middot; Maturity: {{maturityDate}}</p>",
                "Your loan {{loanNumber}} for {{principalAmount}} is active. "
                        + "EMI: {{emiAmount}}. Maturity: {{maturityDate}}.");
        seedTemplate(
                "SCHEDULE_TRIGGERED",
                "SCHEDULE",
                "Scheduled payment started",
                "<p>{{message}}</p>",
                "{{message}}");
        seedTemplate(
                "SCHEDULE_COMPLETED",
                "SCHEDULE",
                "Scheduled payment completed",
                "<p>{{message}}</p>",
                "{{message}}");
        seedTemplate(
                "SCHEDULE_FAILED",
                "SCHEDULE",
                "Scheduled payment failed",
                "<p>{{message}}</p>",
                "{{message}}");
    }

    private void seedTemplate(
            String name,
            String type,
            String subject,
            String htmlBody,
            String plainBody) {
        if (templates.findByNameAndActiveTrue(name).isEmpty()) {
            templates.save(new EmailTemplate(
                    name,
                    type,
                    subject,
                    htmlBody,
                    plainBody));
        }
    }

    @Transactional
    public EmailResponse send(EmailRequest request) {
        EmailTemplate template = templates.findByNameAndActiveTrue(request.templateName())
                .orElseThrow(() -> new IllegalArgumentException("Email template not found"));
        Map<String, String> variables =
                request.variables() == null ? Map.of() : request.variables();

        String subject = render(template.getSubject(), variables);
        String htmlBody = render(template.getHtml(), variables);
        String plainBody = render(template.getPlain(), variables);

        EmailNotification notification = notifications.save(new EmailNotification(
                request.recipient(),
                subject,
                preview(plainBody),
                template.getName(),
                template.getName(),
                request.sourceEvent(),
                request.referenceId()));

        deliver(notification, htmlBody, plainBody);
        return response(notification);
    }

    @Transactional
    public EmailResponse retry(String notificationId) {
        EmailNotification notification = find(notificationId);
        if (notification.getStatus() == NotificationStatus.SENT) {
            return response(notification);
        }

        notification.retrying();
        notifications.save(notification);

        EmailTemplate template = templates
                .findByNameAndActiveTrue(notification.getTemplateName())
                .orElseThrow(() -> new IllegalArgumentException("Email template not found"));
        deliver(notification, template.getHtml(), template.getPlain());
        return response(notification);
    }

    @Scheduled(fixedDelayString = "${notification.retry-delay-ms}")
    @Transactional
    public void retryTemporaryFailures() {
        notifications.findByStatusOrderByCreatedAtDesc(NotificationStatus.RETRYING)
                .stream()
                .filter(notification -> notification.getRetryCount() < maxRetries)
                .forEach(notification -> retry(notification.getId()));
    }

    private void deliver(
            EmailNotification notification,
            String htmlBody,
            String plainBody) {
        int attempt = notification.getRetryCount() + 1;
        notification.processing();
        notifications.save(notification);

        try {
            if (senderEmail == null || senderEmail.isBlank()) {
                throw new IllegalStateException("SMTP sender is not configured");
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, senderName);
            helper.setTo(notification.getRecipient());
            helper.setSubject(notification.getSubject());
            helper.setText(plainBody, htmlBody);

            mailSender.send(message);
            notification.sent();
            deliveryLogs.save(new EmailDeliveryLog(
                    notification.getId(),
                    attempt,
                    "SENT",
                    null,
                    "SMTP accepted message"));
        } catch (Exception exception) {
            if (notification.getRetryCount() < maxRetries) {
                notification.retryScheduled();
            } else {
                notification.failed();
            }

            deliveryLogs.save(new EmailDeliveryLog(
                    notification.getId(),
                    attempt,
                    "FAILED",
                    safeMessage(exception),
                    null));
        } finally {
            notifications.save(notification);
        }
    }

    public EmailResponse get(String notificationId) {
        return response(find(notificationId));
    }

    public List<EmailSummaryResponse> history() {
        return notifications.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::summary)
                .toList();
    }

    public List<EmailSummaryResponse> byStatus(NotificationStatus status) {
        return notifications.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(this::summary)
                .toList();
    }

    private EmailNotification find(String notificationId) {
        return notifications.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Email notification not found"));
    }

    private String render(String source, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(source);
        StringBuffer output = new StringBuffer();

        while (matcher.find()) {
            String replacement = variables.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String preview(String body) {
        return body.substring(0, Math.min(500, body.length()));
    }

    private String safeMessage(Exception exception) {
        return exception.getClass().getSimpleName()
                + ": "
                + String.valueOf(exception.getMessage())
                        .replaceAll("(?i)password=[^\\s]+", "password=***");
    }

    private EmailResponse response(EmailNotification notification) {
        return new EmailResponse(
                notification.getId(),
                notification.getRecipient(),
                notification.getSubject(),
                notification.getStatus(),
                notification.getRetryCount(),
                notification.getCreatedAt(),
                notification.getSentAt());
    }

    private EmailSummaryResponse summary(EmailNotification notification) {
        return new EmailSummaryResponse(
                notification.getId(),
                notification.getRecipient(),
                notification.getSubject(),
                notification.getType(),
                notification.getStatus(),
                notification.getRetryCount(),
                notification.getCreatedAt(),
                notification.getSentAt());
    }
}
