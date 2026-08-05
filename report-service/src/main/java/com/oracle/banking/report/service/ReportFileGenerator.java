package com.oracle.banking.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.oracle.banking.report.entity.ReportFormat;
import com.oracle.banking.report.entity.ReportJob;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReportFileGenerator {
    private final Path storageRoot;
    private final long maxFileSize;

    public ReportFileGenerator(
            @Value("${report.storage.path}") String storagePath,
            @Value("${report.limits.max-file-size-bytes}") long maxFileSize) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    public ReportArtifact generate(ReportJob job, ArrayNode rows) throws Exception {
        Files.createDirectories(storageRoot);
        String extension = job.getReportFormat() == ReportFormat.CSV ? "csv" : "pdf";
        String fileName = job.getReportType().name().toLowerCase().replace('_', '-') + "-" + job.getReportJobId() + "." + extension;
        Path target = storageRoot.resolve(fileName).normalize();
        if (!target.startsWith(storageRoot)) throw new IllegalStateException("Invalid report storage target");
        byte[] bytes = job.getReportFormat() == ReportFormat.CSV ? csv(rows) : pdf(job, rows);
        if (bytes.length > maxFileSize) throw new IllegalStateException("Generated report exceeds configured file-size limit");
        Files.write(target, bytes);
        String contentType = job.getReportFormat() == ReportFormat.CSV ? "text/csv; charset=UTF-8" : "application/pdf";
        return new ReportArtifact(fileName, target.toString(), contentType, bytes.length, sha256(bytes), rows.size());
    }

    private byte[] csv(ArrayNode rows) {
        Set<String> columns = columns(rows);
        StringBuilder output = new StringBuilder("\uFEFF");
        output.append(columns.stream().map(this::csvCell).reduce((a, b) -> a + "," + b).orElse("")).append("\r\n");
        for (JsonNode row : rows) {
            output.append(columns.stream().map(column -> csvCell(value(row.path(column))))
                    .reduce((a, b) -> a + "," + b).orElse("")).append("\r\n");
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] pdf(ReportJob job, ArrayNode rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("NET BANKING - " + job.getReportType().name().replace('_', ' '));
        lines.add("Generated: " + Instant.now());
        lines.add("Rows: " + rows.size());
        lines.add("");
        for (JsonNode row : rows) lines.add(plain(row.toString()));
        return SimplePdf.write(lines);
    }

    private Set<String> columns(ArrayNode rows) {
        Set<String> columns = new LinkedHashSet<>();
        rows.forEach(row -> { if (row.isObject()) row.fieldNames().forEachRemaining(columns::add); });
        return columns;
    }

    private String value(JsonNode node) {
        return node == null || node.isNull() ? "" : node.isValueNode() ? node.asText() : node.toString();
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String plain(String value) {
        String safe = value.replaceAll("[\\r\\n\\t]", " ");
        return safe.length() <= 160 ? safe : safe.substring(0, 160);
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public record ReportArtifact(
            String fileName, String storagePath, String contentType,
            long fileSize, String checksum, int rowCount) {}

    static final class SimplePdf {
        private SimplePdf() {}

        static byte[] write(List<String> lines) throws IOException {
            int linesPerPage = 55;
            int pages = Math.max(1, (lines.size() + linesPerPage - 1) / linesPerPage);
            int fontId = 3 + pages * 2;
            List<byte[]> objects = new ArrayList<>();
            objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
            StringBuilder kids = new StringBuilder();
            for (int page = 0; page < pages; page++) kids.append(3 + page * 2).append(" 0 R ");
            objects.add(bytes("<< /Type /Pages /Kids [" + kids + "] /Count " + pages + " >>"));
            for (int page = 0; page < pages; page++) {
                int contentId = 4 + page * 2;
                objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 "
                        + fontId + " 0 R >> >> /Contents " + contentId + " 0 R >>"));
                StringBuilder content = new StringBuilder("BT /F1 8 Tf 36 806 Td 13 TL ");
                int start = page * linesPerPage;
                int end = Math.min(lines.size(), start + linesPerPage);
                for (int i = start; i < end; i++) content.append('(').append(escape(lines.get(i))).append(") Tj T* ");
                content.append("ET");
                byte[] stream = bytes(content.toString());
                objects.add(bytes("<< /Length " + stream.length + " >>\nstream\n" + content + "\nendstream"));
            }
            objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(bytes("%PDF-1.4\n"));
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (int i = 0; i < objects.size(); i++) {
                offsets.add(out.size());
                out.write(bytes((i + 1) + " 0 obj\n"));
                out.write(objects.get(i));
                out.write(bytes("\nendobj\n"));
            }
            int xref = out.size();
            out.write(bytes("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
            for (int i = 1; i < offsets.size(); i++) out.write(bytes(String.format("%010d 00000 n \n", offsets.get(i))));
            out.write(bytes("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF"));
            return out.toByteArray();
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                    .replaceAll("[^\\x20-\\x7E]", "?");
        }

        private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    }
}
