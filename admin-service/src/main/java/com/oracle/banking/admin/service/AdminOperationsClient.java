package com.oracle.banking.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oracle.banking.admin.dto.AdminDtos.Dashboard;
import com.oracle.banking.admin.dto.AdminDtos.GlobalSearch;
import com.oracle.banking.admin.dto.AdminDtos.Section;
import com.oracle.banking.admin.dto.AdminDtos.SectionStatus;
import com.oracle.banking.admin.dto.AdminDtos.SystemHealth;
import com.oracle.banking.shared.constants.SecurityConstants;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminOperationsClient {
    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
            "customerUserId", "status", "page", "size", "accountId", "transactionType", "workflowType",
            "loanType", "cardType", "operationType", "systemOwned", "scheduleId", "fromDate", "toDate",
            "actorUserId", "action", "sourceService", "entityType", "referenceId", "correlationId", "severity",
            "from", "to");

    private final RestClient client;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    private final String internalApiKey;
    private final int maxPageSize;
    private final int globalGroupSize;
    private final Duration cacheTtl;
    private final Map<String, Resource> resources;
    private final Map<String, CachedSection> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public AdminOperationsClient(
            RestClient.Builder builder, ObjectMapper mapper, ExecutorService executor,
            @Value("${services.internal-api-key}") String internalApiKey,
            @Value("${admin.limits.max-page-size}") int maxPageSize,
            @Value("${admin.limits.global-search-group-size}") int globalGroupSize,
            @Value("${admin.cache.summary-seconds}") long cacheSeconds,
            @Value("${services.auth-service-url}") String auth,
            @Value("${services.customer-service-url}") String customer,
            @Value("${services.branch-service-url}") String branch,
            @Value("${services.account-service-url}") String account,
            @Value("${services.beneficiary-service-url}") String beneficiary,
            @Value("${services.transaction-service-url}") String transaction,
            @Value("${services.workflow-service-url}") String workflow,
            @Value("${services.billpayment-service-url}") String billPayment,
            @Value("${services.card-service-url}") String card,
            @Value("${services.loan-service-url}") String loan,
            @Value("${services.scheduler-service-url}") String scheduler,
            @Value("${services.audit-service-url}") String audit) {
        this.client = builder.build();
        this.mapper = mapper;
        this.executor = executor;
        this.internalApiKey = internalApiKey;
        this.maxPageSize = maxPageSize;
        this.globalGroupSize = globalGroupSize;
        this.cacheTtl = Duration.ofSeconds(cacheSeconds);
        Map<String, Resource> map = new LinkedHashMap<>();
        map.put("users", resource(auth, "users"));
        map.put("customers", resource(customer, "customers"));
        map.put("branches", resource(branch, "branches"));
        map.put("accounts", resource(account, "accounts"));
        map.put("beneficiaries", resource(beneficiary, "beneficiaries"));
        map.put("transactions", resource(transaction, "transactions"));
        map.put("workflows", resource(workflow, "workflows"));
        map.put("bill-payments", resource(billPayment, "bill-payments"));
        map.put("cards", resource(card, "cards"));
        map.put("loans", resource(loan, "loans"));
        map.put("schedules", resource(scheduler, "schedules"));
        map.put("audit", resource(audit, "audit"));
        this.resources = Map.copyOf(map);
    }

    public JsonNode search(String resource, MultiValueMap<String, String> parameters) {
        Resource target = required(resource);
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(target.searchUrl());
        parameters.forEach((name, values) -> {
            if (!ALLOWED_PARAMETERS.contains(name)) return;
            for (String value : values) {
                if ("size".equals(name)) uri.queryParam(name, Math.min(parsePositive(value, 50), maxPageSize));
                else uri.queryParam(name, value);
            }
        });
        if (!parameters.containsKey("size")) uri.queryParam("size", Math.min(50, maxPageSize));
        return get(uri.build(true).toUriString());
    }

    public Dashboard dashboard() {
        Map<String, CompletableFuture<Section>> futures = new LinkedHashMap<>();
        resources.forEach((name, resource) -> futures.put(name,
                CompletableFuture.supplyAsync(() -> summary(name, resource), executor)));
        Map<String, Section> sections = new LinkedHashMap<>();
        futures.forEach((name, future) -> {
            try { sections.put(name, future.join()); }
            catch (RuntimeException ex) { sections.put(name, unavailable(ex)); }
        });
        return new Dashboard(Instant.now(), sections);
    }

    public Section auditSummary() { return summary("audit", required("audit")); }

    public SystemHealth systemHealth() {
        Map<String, CompletableFuture<Section>> futures = new LinkedHashMap<>();
        resources.forEach((name, resource) -> futures.put(name,
                CompletableFuture.supplyAsync(() -> health(resource), executor)));
        Map<String, Section> sections = new LinkedHashMap<>();
        futures.forEach((name, future) -> {
            try { sections.put(name, future.join()); }
            catch (RuntimeException ex) { sections.put(name, unavailable(ex)); }
        });
        return new SystemHealth(Instant.now(), sections);
    }

    public GlobalSearch globalSearch(String query) {
        if (query == null || query.isBlank() || query.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must contain 1 to 64 characters");
        }
        String normalized = query.toLowerCase();
        Map<String, JsonNode> groups = new LinkedHashMap<>();
        for (String name : List.of("users", "customers", "accounts", "beneficiaries", "branches", "loans", "cards")) {
            if ("cards".equals(name) && !query.matches("\\d{1,4}")) continue;
            JsonNode response = search(name, new org.springframework.util.LinkedMultiValueMap<>(Map.of(
                    "page", List.of("0"), "size", List.of(String.valueOf(globalGroupSize)))));
            ArrayNode matches = mapper.createArrayNode();
            for (JsonNode item : response.path("items")) {
                if (item.toString().toLowerCase().contains(normalized) && matches.size() < globalGroupSize) matches.add(item);
            }
            groups.put(name, matches);
        }
        return new GlobalSearch(Instant.now(), query, groups);
    }

    private Section summary(String name, Resource resource) {
        CachedSection cached = cache.get(name);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.section();
        try {
            Section section = new Section(SectionStatus.AVAILABLE, Instant.now(), get(resource.summaryUrl()), null);
            cache.put(name, new CachedSection(section, Instant.now().plus(cacheTtl)));
            return section;
        } catch (RuntimeException ex) { return unavailable(ex); }
    }

    private Section health(Resource resource) {
        try { return new Section(SectionStatus.AVAILABLE, Instant.now(), get(resource.healthUrl()), null); }
        catch (RuntimeException ex) { return unavailable(ex); }
    }

    private Section unavailable(RuntimeException ex) {
        return new Section(SectionStatus.UNAVAILABLE, Instant.now(), null, ex.getClass().getSimpleName());
    }

    private JsonNode get(String url) {
        JsonNode body = client.get().uri(url).header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                .retrieve().body(JsonNode.class);
        return body == null ? mapper.createObjectNode() : body;
    }

    private Resource required(String name) {
        Resource resource = resources.get(name);
        if (resource == null) throw new IllegalArgumentException("Unsupported admin resource");
        return resource;
    }

    private Resource resource(String base, String name) {
        return new Resource(base + "/internal/operations/" + name + "/search",
                base + "/internal/operations/" + name + "/summary", base + "/actuator/health");
    }

    private int parsePositive(String value, int fallback) {
        try { return Math.max(1, Integer.parseInt(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private record Resource(String searchUrl, String summaryUrl, String healthUrl) {}
    private record CachedSection(Section section, Instant expiresAt) {}
}
