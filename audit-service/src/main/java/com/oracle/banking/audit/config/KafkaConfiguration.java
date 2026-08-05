package com.oracle.banking.audit.config;

import com.oracle.banking.audit.service.AuditIngestionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
    @Bean
    DefaultErrorHandler auditErrorHandler(
            AuditIngestionService ingestion,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${audit.kafka.dead-letter-topic}") String deadLetterTopic,
            @Value("${audit.consumer.retry-backoff-ms}") long retryBackoffMs,
            @Value("${audit.consumer.retry-count}") long retryCount) {
        return new DefaultErrorHandler((record, error) -> {
            ingestion.recordFailure(record, error);
            @SuppressWarnings("unchecked")
            ConsumerRecord<String, String> failed = (ConsumerRecord<String, String>) record;
            kafkaTemplate.send(deadLetterTopic, failed.key(), failed.value());
        }, new FixedBackOff(retryBackoffMs, retryCount));
    }
}
