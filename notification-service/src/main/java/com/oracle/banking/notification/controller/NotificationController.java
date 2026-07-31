package com.oracle.banking.notification.controller;

import com.oracle.banking.notification.NotificationStatus;
import com.oracle.banking.notification.dto.NotificationDtos.EmailRequest;
import com.oracle.banking.notification.dto.NotificationDtos.EmailResponse;
import com.oracle.banking.notification.dto.NotificationDtos.EmailSummaryResponse;
import com.oracle.banking.notification.dto.NotificationDtos.SendTestEmailRequest;
import com.oracle.banking.notification.service.NotificationEventPublisher;
import com.oracle.banking.notification.service.NotificationService;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/email")
public class NotificationController {
    private final NotificationService service;
    private final NotificationEventPublisher events;

    public NotificationController(
            NotificationService service,
            NotificationEventPublisher events) {
        this.service = service;
        this.events = events;
    }

    @PostMapping("/send")
    ApiResponse<EmailResponse> send(@Valid @RequestBody EmailRequest request) {
        return ApiResponse.success("Email processed", service.send(request));
    }

    @PostMapping("/test")
    ApiResponse<EmailResponse> test(@Valid @RequestBody SendTestEmailRequest request) {
        EmailRequest emailRequest = new EmailRequest(
                request.recipient(),
                "GENERIC_NOTIFICATION",
                request.variables(),
                "manual-test",
                null);
        return ApiResponse.success("Test email processed", service.send(emailRequest));
    }

    @PostMapping("/test-kafka")
    ApiResponse<String> testKafka(@Valid @RequestBody SendTestEmailRequest request) {
        return ApiResponse.success(
                "Kafka test event published",
                events.publishTest(request.recipient(), request.variables()));
    }

    @GetMapping("/{id}")
    ApiResponse<EmailResponse> get(@PathVariable String id) {
        return ApiResponse.success("Email notification", service.get(id));
    }

    @GetMapping("/history")
    ApiResponse<List<EmailSummaryResponse>> history() {
        return ApiResponse.success("Email history", service.history());
    }

    @PostMapping("/{id}/retry")
    ApiResponse<EmailResponse> retry(@PathVariable String id) {
        return ApiResponse.success("Email retry processed", service.retry(id));
    }

    @GetMapping("/failed")
    ApiResponse<List<EmailSummaryResponse>> failed() {
        return ApiResponse.success(
                "Failed emails",
                service.byStatus(NotificationStatus.FAILED));
    }

    @GetMapping("/pending")
    ApiResponse<List<EmailSummaryResponse>> pending() {
        return ApiResponse.success(
                "Pending emails",
                service.byStatus(NotificationStatus.PENDING));
    }
}
