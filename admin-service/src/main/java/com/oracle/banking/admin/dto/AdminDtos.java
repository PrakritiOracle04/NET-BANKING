package com.oracle.banking.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;

public final class AdminDtos {
    private AdminDtos() {}

    public enum SectionStatus { AVAILABLE, DEGRADED, UNAVAILABLE }

    public record Section(SectionStatus status, Instant asOf, JsonNode data, String error) {}
    public record Dashboard(Instant generatedAt, Map<String, Section> sections) {}
    public record SystemHealth(Instant generatedAt, Map<String, Section> services) {}
    public record GlobalSearch(Instant generatedAt, String query, Map<String, JsonNode> groups) {}
}
