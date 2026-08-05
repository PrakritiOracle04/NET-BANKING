package com.oracle.banking.admin.config;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfiguration {
    @Bean
    RestClient.Builder restClientBuilder(
            @Value("${admin.client.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${admin.client.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder().requestFactory(factory);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService adminExecutor(@Value("${admin.executor.threads}") int threads) {
        return Executors.newFixedThreadPool(threads);
    }
}
