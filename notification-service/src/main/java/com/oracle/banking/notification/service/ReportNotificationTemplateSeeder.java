package com.oracle.banking.notification.service;

import com.oracle.banking.notification.entity.EmailTemplate;
import com.oracle.banking.notification.repository.EmailTemplateRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ReportNotificationTemplateSeeder {
    private final EmailTemplateRepository templates;

    public ReportNotificationTemplateSeeder(EmailTemplateRepository templates) {
        this.templates = templates;
    }

    @PostConstruct
    void seed() {
        upsert(
                "REPORT_READY",
                "Your {{reportType}} report is ready",
                "<div style=\"font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937\">"
                        + "<h2>Your report is ready</h2><p>Your requested <strong>{{reportType}}</strong> report "
                        + "has been generated successfully.</p><p>Open Oracle Banking and use report reference "
                        + "<strong>{{reportId}}</strong> to view or download it.</p>"
                        + "<p style=\"font-size:12px;color:#6b7280\">This is an automated message from Oracle Banking.</p></div>",
                "Your {{reportType}} report is ready. Open Oracle Banking and use report reference {{reportId}}.");
        upsert(
                "REPORT_FAILED",
                "Your {{reportType}} report could not be generated",
                "<div style=\"font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937\">"
                        + "<h2>Report generation failed</h2><p>We could not generate your "
                        + "<strong>{{reportType}}</strong> report.</p><p>Report reference: <strong>{{reportId}}</strong></p>"
                        + "<p>Reason: {{reason}}</p><p>Please correct the request details or try again later.</p></div>",
                "Report {{reportId}} for {{reportType}} could not be generated: {{reason}}. Please try again later.");
    }

    private void upsert(String name, String subject, String html, String plain) {
        EmailTemplate template = templates.findByName(name)
                .orElseGet(() -> new EmailTemplate(name, "REPORT", subject, html, plain));
        template.applySeedContent("REPORT", subject, html, plain);
        templates.save(template);
    }
}
