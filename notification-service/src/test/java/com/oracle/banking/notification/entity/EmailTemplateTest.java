package com.oracle.banking.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailTemplateTest {
    @Test
    void seededContentIsActiveAndUsesCurrentVersion() {
        EmailTemplate template = new EmailTemplate(
                "WELCOME", "WELCOME", "Subject", "<p>A complete message</p>", "A complete message");

        assertThat(template.isActive()).isTrue();
        assertThat(template.getVersion()).isEqualTo(EmailTemplate.CURRENT_SEED_VERSION);
        assertThat(template.getHtml()).contains("complete message");
    }
}
