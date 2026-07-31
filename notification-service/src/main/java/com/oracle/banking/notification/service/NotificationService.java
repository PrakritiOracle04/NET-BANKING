package com.oracle.banking.notification.service;

import com.oracle.banking.notification.NotificationStatus;
import com.oracle.banking.notification.dto.NotificationDtos.EmailRequest;
import com.oracle.banking.notification.dto.NotificationDtos.EmailResponse;
import com.oracle.banking.notification.dto.NotificationDtos.EmailSummaryResponse;
import com.oracle.banking.notification.dto.NotificationDtos.DeliveryAttemptResponse;
import com.oracle.banking.notification.entity.EmailDeliveryLog;
import com.oracle.banking.notification.entity.EmailNotification;
import com.oracle.banking.notification.entity.EmailTemplate;
import com.oracle.banking.notification.repository.EmailDeliveryLogRepository;
import com.oracle.banking.notification.repository.EmailNotificationRepository;
import com.oracle.banking.notification.repository.EmailTemplateRepository;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([A-Za-z0-9_.-]+)}}");
    private final EmailNotificationRepository notifications; private final EmailTemplateRepository templates;
    private final EmailDeliveryLogRepository logs; private final JavaMailSender mailSender; private final String senderEmail, senderName; private final int maxRetries;
    public NotificationService(EmailNotificationRepository notifications, EmailTemplateRepository templates, EmailDeliveryLogRepository logs, JavaMailSender mailSender,
            @Value("${notification.sender-email}") String senderEmail, @Value("${notification.sender-name}") String senderName,
            @Value("${notification.max-retries}") int maxRetries) { this.notifications=notifications; this.templates=templates; this.logs=logs; this.mailSender=mailSender; this.senderEmail=senderEmail; this.senderName=senderName; this.maxRetries=maxRetries; }
    @PostConstruct void defaults(){ seed("WELCOME","WELCOME","Welcome to Oracle Banking, {{customerName}}","<h2>Welcome, {{customerName}}</h2><p>Your Oracle Banking profile is ready.</p>","Welcome, {{customerName}}. Your Oracle Banking profile is ready."); seed("LOGIN_ALERT","LOGIN_ALERT","New login to your Oracle Banking account","<h2>Login alert</h2><p>Hello {{customerName}}, a login was detected at {{currentTime}}.</p>","Hello {{customerName}}, a login was detected at {{currentTime}}."); seed("PASSWORD_RESET","PASSWORD_RESET","Reset your Oracle Banking password","<h2>Password reset</h2><p>Use this link: {{verificationLink}}</p>","Reset link: {{verificationLink}}"); seed("GENERIC_NOTIFICATION","GENERIC","Oracle Banking notification","<p>{{message}}</p>","{{message}}"); }
    private void seed(String name,String type,String subject,String html,String plain){ if(templates.findByNameAndActiveTrue(name).isEmpty()) templates.save(new EmailTemplate(name,type,subject,html,plain)); }
    @Transactional public EmailResponse send(EmailRequest request){ EmailTemplate template=templates.findByNameAndActiveTrue(request.templateName()).orElseThrow(()->new IllegalArgumentException("Email template not found")); Map<String,String> values=request.variables()==null?Map.of():request.variables(); String subject=render(template.getSubject(),values), html=render(template.getHtml(),values), plain=render(template.getPlain(),values); EmailNotification notification=notifications.save(new EmailNotification(request.recipient(),subject,preview(plain),template.getName(),template.getName(),request.sourceEvent(),request.referenceId())); deliver(notification,html,plain); return response(notification); }
    @Transactional public EmailResponse retry(String id){ EmailNotification notification=find(id); if(notification.getStatus()==NotificationStatus.SENT) return response(notification); notification.retrying(); notifications.save(notification); EmailTemplate template=templates.findByNameAndActiveTrue(notification.getTemplateName()).orElseThrow(()->new IllegalArgumentException("Email template not found")); deliver(notification,template.getHtml(),template.getPlain()); return response(notification); }
    @Scheduled(fixedDelayString="${notification.retry-delay-ms}") @Transactional public void retryTemporaryFailures(){ notifications.findByStatusOrderByCreatedAtDesc(NotificationStatus.RETRYING).stream().filter(n->n.getRetryCount()<maxRetries).forEach(n->retry(n.getId())); }
    private void deliver(EmailNotification n,String html,String plain){ int attempt=n.getRetryCount()+1; n.processing(); notifications.save(n); try { if(senderEmail==null||senderEmail.isBlank()) throw new IllegalStateException("SMTP sender is not configured"); MimeMessage message=mailSender.createMimeMessage(); MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8"); helper.setFrom(senderEmail,senderName); helper.setTo(n.getRecipient()); helper.setSubject(n.getSubject()); helper.setText(plain,html); mailSender.send(message); n.sent(); logs.save(new EmailDeliveryLog(n.getId(),attempt,"SENT",null,"SMTP accepted message")); log.info("SMTP delivery accepted notificationId={} reference={} attempt={} status={}",n.getId(),n.getReferenceId(),attempt,n.getStatus()); } catch(Exception ex){ if(n.getRetryCount()<maxRetries) n.retryScheduled(); else n.failed(); String reason=safe(ex); logs.save(new EmailDeliveryLog(n.getId(),attempt,"FAILED",reason,null)); log.warn("SMTP delivery failed notificationId={} reference={} attempt={} status={} reason={}",n.getId(),n.getReferenceId(),attempt,n.getStatus(),reason); } finally { notifications.save(n); } }
    public EmailResponse get(String id){return response(find(id));} public EmailResponse byReference(String referenceId){return response(notifications.findFirstByReferenceIdOrderByCreatedAtDesc(referenceId).orElseThrow(()->new IllegalArgumentException("Email notification not found for reference")));} public List<DeliveryAttemptResponse> deliveryAttempts(String id){find(id);return logs.findByNotificationIdOrderByAttemptAsc(id).stream().map(l->new DeliveryAttemptResponse(l.getId(),l.getAttempt(),l.getStatus(),l.getFailureReason(),l.getSmtpResponse(),l.getTimestamp())).toList();} public List<EmailSummaryResponse> history(){return notifications.findAllByOrderByCreatedAtDesc().stream().map(this::summary).toList();} public List<EmailSummaryResponse> byStatus(NotificationStatus s){return notifications.findByStatusOrderByCreatedAtDesc(s).stream().map(this::summary).toList();}
    private EmailNotification find(String id){return notifications.findById(id).orElseThrow(()->new IllegalArgumentException("Email notification not found"));} private String render(String source,Map<String,String> v){Matcher m=VARIABLE.matcher(source);StringBuffer out=new StringBuffer();while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(v.getOrDefault(m.group(1),"")));m.appendTail(out);return out.toString();} private String preview(String b){return b.substring(0,Math.min(500,b.length()));} private String safe(Exception ex){return ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage()).replaceAll("(?i)password=[^\\s]+","password=***");} private EmailResponse response(EmailNotification n){return new EmailResponse(n.getId(),n.getRecipient(),n.getSubject(),n.getType(),n.getSourceEvent(),n.getReferenceId(),n.getStatus(),n.getRetryCount(),n.getCreatedAt(),n.getSentAt());} private EmailSummaryResponse summary(EmailNotification n){return new EmailSummaryResponse(n.getId(),n.getRecipient(),n.getSubject(),n.getType(),n.getSourceEvent(),n.getReferenceId(),n.getStatus(),n.getRetryCount(),n.getCreatedAt(),n.getSentAt());}
}
