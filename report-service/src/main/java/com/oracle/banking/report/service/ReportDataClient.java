package com.oracle.banking.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oracle.banking.report.entity.ReportJob;
import com.oracle.banking.report.entity.ReportType;
import com.oracle.banking.shared.constants.SecurityConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ReportDataClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String internalApiKey;
    private final int maxRows;
    private final Map<ReportType, String> searchUrls;
    private final Map<String, String> summaryUrls;
    private final String scheduleExecutionsUrl;

    public ReportDataClient(
            RestClient.Builder builder, ObjectMapper mapper,
            @Value("${services.internal-api-key}") String internalApiKey,
            @Value("${report.limits.max-rows}") int maxRows,
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
        this.internalApiKey = internalApiKey;
        this.maxRows = maxRows;
        this.searchUrls = Map.of(
                ReportType.ACCOUNT_STATEMENT, transaction + "/internal/operations/transactions/statement",
                ReportType.TRANSACTIONS, transaction + "/internal/operations/transactions/search",
                ReportType.CUSTOMERS, customer + "/internal/operations/customers/search",
                ReportType.CARDS, card + "/internal/operations/cards/search",
                ReportType.LOANS, loan + "/internal/operations/loans/search",
                ReportType.BILL_PAYMENTS, billPayment + "/internal/operations/bill-payments/search",
                ReportType.SCHEDULES, scheduler + "/internal/operations/schedules/search",
                ReportType.AUDIT, audit + "/internal/operations/audit/search");
        this.scheduleExecutionsUrl = scheduler + "/internal/operations/schedules/executions/search";
        Map<String, String> summaries = new LinkedHashMap<>();
        summaries.put("users", auth + "/internal/operations/users/summary");
        summaries.put("customers", customer + "/internal/operations/customers/summary");
        summaries.put("branches", branch + "/internal/operations/branches/summary");
        summaries.put("accounts", account + "/internal/operations/accounts/summary");
        summaries.put("beneficiaries", beneficiary + "/internal/operations/beneficiaries/summary");
        summaries.put("transactions", transaction + "/internal/operations/transactions/summary");
        summaries.put("workflows", workflow + "/internal/operations/workflows/summary");
        summaries.put("billPayments", billPayment + "/internal/operations/bill-payments/summary");
        summaries.put("cards", card + "/internal/operations/cards/summary");
        summaries.put("loans", loan + "/internal/operations/loans/summary");
        summaries.put("schedules", scheduler + "/internal/operations/schedules/summary");
        summaries.put("audit", audit + "/internal/operations/audit/summary");
        this.summaryUrls = Map.copyOf(summaries);
    }

    public ArrayNode fetch(ReportJob job) {
        JsonNode snapshot = read(job.getFilterSnapshot());
        if (job.getReportType() == ReportType.ADMIN_OVERVIEW) return overview();
        ArrayNode rows = fetchPages(searchUrls.get(job.getReportType()), snapshot);
        if (job.getReportType() == ReportType.SCHEDULES && rows.size() < maxRows) {
            ArrayNode executions = fetchPages(scheduleExecutionsUrl, snapshot);
            executions.forEach(node -> {
                if (rows.size() < maxRows && node.isObject()) {
                    ((ObjectNode) node).put("recordType", "EXECUTION");
                    rows.add(node);
                }
            });
        }
        return rows;
    }

    private ArrayNode fetchPages(String baseUrl, JsonNode snapshot) {
        ArrayNode rows = mapper.createArrayNode();
        int page = 0;
        int pageSize = Math.min(100, maxRows);
        while (rows.size() < maxRows) {
            UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl)
                    .queryParam("page", page).queryParam("size", Math.min(pageSize, maxRows - rows.size()));
            add(uri, "customerUserId", snapshot.path("ownerUserId").asText(null));
            add(uri, "accountId", snapshot.path("accountId").asText(null));
            String fromName = baseUrl.contains("/audit/") ? "from" : "fromDate";
            String toName = baseUrl.contains("/audit/") ? "to" : "toDate";
            add(uri, fromName, snapshot.path("from").asText(null));
            add(uri, toName, snapshot.path("to").asText(null));
            JsonNode filters = snapshot.path("filters");
            if (filters.isObject()) filters.fields().forEachRemaining(entry -> add(uri, entry.getKey(), entry.getValue().asText()));
            JsonNode response = get(uri.build(true).toUriString());
            JsonNode items = response.path("items");
            if (!items.isArray() || items.isEmpty()) break;
            items.forEach(rows::add);
            if (page + 1 >= response.path("totalPages").asInt(page + 1)) break;
            page++;
        }
        return rows;
    }

    private ArrayNode overview() {
        ArrayNode rows = mapper.createArrayNode();
        summaryUrls.forEach((name, url) -> {
            ObjectNode row = mapper.createObjectNode();
            row.put("section", name);
            row.set("summary", get(url));
            rows.add(row);
        });
        return rows;
    }

    private JsonNode get(String url) {
        return client.get().uri(url)
                .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                .retrieve().body(JsonNode.class);
    }

    private JsonNode read(String json) {
        try { return mapper.readTree(json); }
        catch (Exception ex) { throw new IllegalStateException("Stored report filters are invalid", ex); }
    }

    private void add(UriComponentsBuilder uri, String name, String value) {
        if (value != null && !value.isBlank() && !"null".equals(value)) uri.queryParam(name, value);
    }
}
