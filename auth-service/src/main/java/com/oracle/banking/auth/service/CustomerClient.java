package com.oracle.banking.auth.service;

import com.oracle.banking.auth.dto.CustomerProfileCreate;
import com.oracle.banking.auth.dto.RegisterRequest;
import com.oracle.banking.auth.entity.AppUser;
import com.oracle.banking.shared.constants.SecurityConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CustomerClient {
    private final RestClient client;
    private final String customerServiceUrl;
    private final String internalKey;

    public CustomerClient(RestClient client,
                          @Value("${services.customer.base-url}") String customerServiceUrl,
                          @Value("${services.internal-api-key}") String internalKey) {
        this.client = client;
        this.customerServiceUrl = customerServiceUrl;
        this.internalKey = internalKey;
    }

    public void createProfile(AppUser user, RegisterRequest request) {
        client.post().uri(customerServiceUrl + "/internal/customers")
                .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalKey)
                .body(new CustomerProfileCreate(user.getUserId(), request.fullName()))
                .retrieve().toBodilessEntity();
    }
}
