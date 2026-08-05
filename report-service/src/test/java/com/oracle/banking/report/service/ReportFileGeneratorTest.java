package com.oracle.banking.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.oracle.banking.report.entity.ReportFormat;
import com.oracle.banking.report.entity.ReportJob;
import com.oracle.banking.report.entity.ReportType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class ReportFileGeneratorTest {
    @TempDir Path tempDirectory;

    @Test
    void csvNeutralizesSpreadsheetFormulaInjection() throws Exception {
        ArrayNode rows = new ObjectMapper().createArrayNode();
        rows.addObject().put("description", "=HYPERLINK(\"bad\")").put("amount", "10.00");
        ReportJob job = new ReportJob("user-1", "CUSTOMER", ReportType.TRANSACTIONS,
                ReportFormat.CSV, "{}", "fingerprint", null);

        var artifact = new ReportFileGenerator(tempDirectory.toString(), 1_000_000).generate(job, rows);
        String csv = Files.readString(Path.of(artifact.storagePath()), StandardCharsets.UTF_8);

        assertThat(csv).contains("'=HYPERLINK");
        assertThat(artifact.contentType()).startsWith("text/csv");
    }

    @Test
    void pdfGeneratorCreatesAValidPdfHeader() throws Exception {
        ArrayNode rows = new ObjectMapper().createArrayNode();
        rows.addObject().put("loanType", "HOME").put("status", "ACTIVE");
        ReportJob job = new ReportJob("admin-1", "ADMIN", ReportType.LOANS,
                ReportFormat.PDF, "{}", "fingerprint", null);

        var artifact = new ReportFileGenerator(tempDirectory.toString(), 1_000_000).generate(job, rows);
        byte[] content = Files.readAllBytes(Path.of(artifact.storagePath()));

        assertThat(new String(content, 0, 8, StandardCharsets.US_ASCII)).isEqualTo("%PDF-1.4");
        assertThat(artifact.rowCount()).isEqualTo(1);
    }
}
