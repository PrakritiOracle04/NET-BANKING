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
                html("Welcome to Oracle Banking", "Hello {{customerName}},",
                        "Your internet banking profile has been created successfully. You can now sign in, "
                                + "complete your customer profile, open accounts and use the banking services available to you.",
                        "For your security, never share your password, OTP or card credentials with anyone."),
                "Hello {{customerName}}, your Oracle Banking profile has been created successfully. "
                        + "You can now sign in and use the available banking services. Never share your password or OTP.");
        seedTemplate(
                "LOGIN_ALERT",
                "LOGIN_ALERT",
                "New login to your Oracle Banking account",
                html("New login detected", "Hello {{customerName}},",
                        "A successful login to your Oracle Banking account was recorded at <strong>{{currentTime}}</strong>.",
                        "If this was not you, reset your password immediately and contact support."),
                "Hello {{customerName}}, a successful login was recorded at {{currentTime}}. "
                        + "If this was not you, reset your password and contact support immediately.");
        seedTemplate(
                "PASSWORD_RESET",
                "PASSWORD_RESET",
                "Reset your Oracle Banking password",
                html("Password reset requested", "We received a request to reset your Oracle Banking password.",
                        "Use the secure link below to continue:<br><a href=\"{{verificationLink}}\">Reset my password</a>",
                        "If you did not request this change, ignore this email and keep your credentials private."),
                "We received a password reset request. Continue at {{verificationLink}}. "
                        + "Ignore this message if you did not make the request.");
        seedTemplate(
                "PASSWORD_RESET_OTP",
                "PASSWORD_RESET",
                "Your Oracle Banking password reset code",
                html("Password reset code", "Hello {{customerName}},",
                        "Enter the following one-time code to verify your password reset request:"
                                + "<div style=\"font-size:30px;font-weight:700;letter-spacing:8px;margin:20px 0\">{{otpCode}}</div>",
                        "This code expires in {{expiresInMinutes}} minutes. Do not share it with anyone. "
                                + "If you did not request a reset, no action is required."),
                "Hello {{customerName}}, your Oracle Banking password reset code is {{otpCode}}. "
                        + "It expires in {{expiresInMinutes}} minutes.");
        seedTemplate(
                "PASSWORD_CHANGED",
                "PASSWORD_RESET",
                "Your Oracle Banking password was changed",
                html("Password changed", "Hello {{customerName}},",
                        "The password for your Oracle Banking account was changed successfully at <strong>{{changedAt}}</strong>.",
                        "If you did not make this change, contact support immediately so your account can be secured."),
                "Hello {{customerName}}, your password was changed at {{changedAt}}. "
                        + "If this was not you, contact support immediately.");
        seedTemplate(
                "GENERIC_NOTIFICATION",
                "GENERIC",
                "Oracle Banking notification",
                html("Oracle Banking notification", "Hello,", "{{message}}",
                        "This is an automated service notification. Please sign in to Oracle Banking if action is required."),
                "Oracle Banking notification: {{message}}");
        seedTemplate(
                "LOAN_CREATED",
                "LOAN",
                "Your Oracle Banking loan {{loanNumber}} is active",
                html("Your loan is active", "Your loan account <strong>{{loanNumber}}</strong> has been created successfully.",
                        "Principal amount: <strong>{{principalAmount}}</strong><br>"
                                + "EMI amount: <strong>{{emiAmount}}</strong><br>"
                                + "Maturity date: <strong>{{maturityDate}}</strong>",
                        "Please maintain sufficient account balance before every EMI due date to avoid overdue charges."),
                "Your loan {{loanNumber}} for {{principalAmount}} is active. "
                        + "EMI: {{emiAmount}}. Maturity: {{maturityDate}}.");
        seedTemplate(
                "SCHEDULE_TRIGGERED",
                "SCHEDULE",
                "Scheduled payment started",
                html("Scheduled payment started", "Your scheduled payment is now being processed.", "{{message}}",
                        "You will receive another notification after processing completes."),
                "Scheduled payment started. {{message}} You will be notified when processing completes.");
        seedTemplate(
                "SCHEDULE_COMPLETED",
                "SCHEDULE",
                "Scheduled payment completed",
                html("Scheduled payment completed", "Your scheduled payment was completed successfully.", "{{message}}",
                        "Review the transaction in Oracle Banking if you need the final reference or account details."),
                "Scheduled payment completed successfully. {{message}}");
        seedTemplate(
                "SCHEDULE_FAILED",
                "SCHEDULE",
                "Scheduled payment failed",
                html("Scheduled payment failed", "We could not complete your scheduled payment.", "{{message}}",
                        "Check the payment details and available balance before retrying. No successful debit is recorded for a failed payment."),
                "Scheduled payment failed. {{message}} Check the details and available balance before retrying.");
        seedTemplate(
                "CARD_APPLICATION_RECEIVED",
                "CARD",
                "Card application received",
                html("Card application received", "We have received your application for a {{cardProduct}} {{cardType}} card.",
                        "The application is pending administrative review. We will notify you after a decision is recorded.",
                        "Submitting an application does not mean that the card has already been issued."),
                "We received your {{cardProduct}} {{cardType}} card application. It is pending review.");
        seedTemplate(
                "CARD_APPLICATION_APPROVED",
                "CARD",
                "Card application approved",
                html("Card application approved", "Your {{cardProduct}} {{cardType}} card application has been approved.",
                        "Card issuance will now proceed using the approved application details.",
                        "You can review the card status and available controls after the card appears in Oracle Banking."),
                "Your {{cardProduct}} {{cardType}} card application has been approved. Card issuance will now proceed.");
        seedTemplate(
                "CARD_APPLICATION_REJECTED",
                "CARD",
                "Card application update",
                html("Card application update", "Your card application has been reviewed and was not approved.",
                        "No card has been issued for this application.",
                        "Review your application details before submitting a new request or contact support if you need clarification."),
                "Your card application was reviewed and not approved. No card has been issued.");
        seedTemplate(
                "LOAN_APPLICATION_RECEIVED",
                "LOAN",
                "Loan application received",
                html("Loan application received", "We have received your {{loanType}} loan application.",
                        "The submitted financial and eligibility information is pending administrative review.",
                        "We will notify you when the application is approved or rejected. No loan account exists until approval."),
                "We received your {{loanType}} loan application. It is pending review and no loan is active yet.");
        seedTemplate(
                "LOAN_APPLICATION_APPROVED",
                "LOAN",
                "Loan application approved",
                html("Loan application approved", "Your {{loanType}} loan application has been approved.",
                        "Loan creation and repayment scheduling will proceed using the approved application terms.",
                        "Review the final loan account, EMI and maturity details in Oracle Banking once processing completes."),
                "Your {{loanType}} loan application has been approved. Review the final loan terms when processing completes.");
        seedTemplate(
                "LOAN_APPLICATION_REJECTED",
                "LOAN",
                "Loan application update",
                html("Loan application update", "Your loan application has been reviewed and was not approved.",
                        "No loan account or repayment schedule has been created for this application.",
                        "You may review your information before applying again or contact support for assistance."),
                "Your loan application was reviewed and not approved. No loan account was created.");
    }

    private void seedTemplate(
            String name,
            String type,
            String subject,
            String htmlBody,
            String plainBody) {
        EmailTemplate template = templates.findByName(name)
                .orElseGet(() -> new EmailTemplate(name, type, subject, htmlBody, plainBody));
        template.applySeedContent(type, subject, htmlBody, plainBody);
        templates.save(template);
    }

    private static String html(String heading, String introduction, String details, String closing) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937\">"
                + "<div style=\"background:#7f1d1d;color:#ffffff;padding:18px 24px\">"
                + "<strong style=\"font-size:20px\">Oracle Banking</strong></div>"
                + "<div style=\"padding:24px;border:1px solid #e5e7eb\">"
                + "<h2 style=\"margin-top:0\">" + heading + "</h2>"
                + "<p>" + introduction + "</p><p>" + details + "</p><p>" + closing + "</p>"
                + "<p style=\"font-size:12px;color:#6b7280;margin-top:28px\">"
                + "This is an automated message from Oracle Banking. Please do not reply.</p></div></div>";
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
