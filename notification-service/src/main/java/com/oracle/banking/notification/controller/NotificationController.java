package com.oracle.banking.notification.controller;
import com.oracle.banking.notification.NotificationStatus; import com.oracle.banking.notification.dto.NotificationDtos.*; import com.oracle.banking.notification.service.NotificationEventPublisher; import com.oracle.banking.notification.service.NotificationService; import com.oracle.banking.shared.response.ApiResponse; import jakarta.validation.Valid; import java.util.List; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/notifications/email") public class NotificationController {
 private final NotificationService service; private final NotificationEventPublisher events; public NotificationController(NotificationService service, NotificationEventPublisher events){this.service=service;this.events=events;}
 @PostMapping("/send") ApiResponse<EmailResponse> send(@Valid @RequestBody EmailRequest r){return ApiResponse.success("Email processed",service.send(r));}
 @PostMapping("/test") ApiResponse<EmailResponse> test(@Valid @RequestBody SendTestEmailRequest r){return ApiResponse.success("Test email processed",service.send(new EmailRequest(r.recipient(),"GENERIC_NOTIFICATION",r.variables(),"manual-test",null)));}
 @PostMapping("/test-kafka") ApiResponse<String> testKafka(@Valid @RequestBody SendTestEmailRequest r){return ApiResponse.success("Kafka test event published",events.publishTest(r.recipient(),r.variables()));}
 @GetMapping("/by-reference/{referenceId}") ApiResponse<EmailResponse> byReference(@PathVariable String referenceId){return ApiResponse.success("Email notification trace",service.byReference(referenceId));}
 @GetMapping("/{id}/delivery-attempts") ApiResponse<List<DeliveryAttemptResponse>> deliveryAttempts(@PathVariable String id){return ApiResponse.success("Email delivery attempts",service.deliveryAttempts(id));}
 @GetMapping("/{id}") ApiResponse<EmailResponse> get(@PathVariable String id){return ApiResponse.success("Email notification",service.get(id));}
 @GetMapping("/history") ApiResponse<List<EmailSummaryResponse>> history(){return ApiResponse.success("Email history",service.history());}
 @PostMapping("/{id}/retry") ApiResponse<EmailResponse> retry(@PathVariable String id){return ApiResponse.success("Email retry processed",service.retry(id));}
 @GetMapping("/failed") ApiResponse<List<EmailSummaryResponse>> failed(){return ApiResponse.success("Failed emails",service.byStatus(NotificationStatus.FAILED));}
 @GetMapping("/pending") ApiResponse<List<EmailSummaryResponse>> pending(){return ApiResponse.success("Pending emails",service.byStatus(NotificationStatus.PENDING));}
}
