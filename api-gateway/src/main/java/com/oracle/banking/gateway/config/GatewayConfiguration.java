package com.oracle.banking.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfiguration {
    private static final Logger log = LoggerFactory.getLogger(GatewayConfiguration.class);

    @Bean
    GlobalFilter requestLoggingFilter() {
        return (exchange, chain) -> {
            log.info("Gateway request: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath());
            return chain.filter(exchange)
                    .doOnSuccess(ignored -> log.info("Gateway response: {}",
                            exchange.getResponse().getStatusCode()));
        };
    }
}
