package com.oracle.banking.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "EMAIL_TEMPLATE")
public class EmailTemplate {
    public static final int CURRENT_SEED_VERSION = 2;

    @Id
    @Column(name = "TEMPLATE_ID", length = 36)
    private String id;

    @Column(name = "TEMPLATE_NAME", unique = true, nullable = false, length = 80)
    private String name;

    @Column(name = "TEMPLATE_TYPE", nullable = false, length = 60)
    private String type;

    @Column(name = "SUBJECT_TEMPLATE", nullable = false, length = 250)
    private String subject;

    @Lob
    @Column(name = "HTML_BODY", nullable = false)
    private String html;

    @Lob
    @Column(name = "PLAIN_TEXT_BODY", nullable = false)
    private String plain;

    @Column(name = "VERSION", nullable = false)
    private int version;

    @Column(name = "ACTIVE", nullable = false)
    private boolean active;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    protected EmailTemplate() {}

    public EmailTemplate(String name, String type, String subject, String html, String plain) {
        id = UUID.randomUUID().toString();
        this.name = name;
        createdAt = Instant.now();
        applySeedContent(type, subject, html, plain);
    }

    public void applySeedContent(String type, String subject, String html, String plain) {
        this.type = type;
        this.subject = subject;
        this.html = html;
        this.plain = plain;
        version = CURRENT_SEED_VERSION;
        active = true;
    }

    public String getName() {
        return name;
    }

    public String getSubject() {
        return subject;
    }

    public String getHtml() {
        return html;
    }

    public String getPlain() {
        return plain;
    }

    public int getVersion() {
        return version;
    }

    public boolean isActive() {
        return active;
    }
}
