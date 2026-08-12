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
        if (templates.findByNameAndActiveTrue("REPORT_READY").isEmpty()) {
            templates.save(new EmailTemplate(
                    "REPORT_READY", "REPORT", "Your {{reportType}} report is ready",
                    "<h2>Report ready</h2><p>Your {{reportType}} report is ready.</p>"
                            + "<p>Open Oracle Banking and use report reference <strong>{{reportId}}</strong>.</p>",
                    "Your {{reportType}} report is ready. Open Oracle Banking and use report reference {{reportId}}."));
        }
        if (templates.findByNameAndActiveTrue("REPORT_FAILED").isEmpty()) {
            templates.save(new EmailTemplate(
                    "REPORT_FAILED", "REPORT", "Your {{reportType}} report could not be generated",
                    "<h2>Report generation failed</h2><p>Report reference: {{reportId}}</p><p>{{reason}}</p>",
                    "Report {{reportId}} could not be generated: {{reason}}"));
        }
    }
}
