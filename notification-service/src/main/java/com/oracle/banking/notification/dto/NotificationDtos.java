package com.oracle.banking.notification.dto;
import com.oracle.banking.notification.NotificationStatus; import jakarta.validation.constraints.Email; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size; import java.time.Instant; import java.util.Map;
public final class NotificationDtos { private NotificationDtos(){}
 public record EmailRequest(@Email @NotBlank String recipient,@NotBlank @Size(max=80) String templateName,Map<String,String> variables,String sourceEvent,String referenceId){}
 public record SendTestEmailRequest(@Email @NotBlank String recipient,Map<String,String> variables){}
 public record EmailResponse(String notificationId,String recipient,String subject,NotificationStatus status,int retryCount,Instant createdAt,Instant sentAt){}
 public record EmailSummaryResponse(String notificationId,String recipient,String subject,String type,NotificationStatus status,int retryCount,Instant createdAt,Instant sentAt){}
}
