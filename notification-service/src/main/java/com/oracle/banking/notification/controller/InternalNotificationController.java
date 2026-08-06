package com.oracle.banking.notification.controller;

import com.oracle.banking.notification.dto.NotificationDtos.EmailRequest;
import com.oracle.banking.notification.dto.NotificationDtos.EmailResponse;
import com.oracle.banking.notification.service.NotificationService;
import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications/email")
public class InternalNotificationController {
    private final NotificationService service;
    private final String internalApiKey;

    public InternalNotificationController(
            NotificationService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/template")
    ResponseEntity<ApiResponse<EmailResponse>> template(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @Valid @RequestBody EmailRequest request) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(ApiResponse.success("Email template processed", service.send(request)));
    }
}
