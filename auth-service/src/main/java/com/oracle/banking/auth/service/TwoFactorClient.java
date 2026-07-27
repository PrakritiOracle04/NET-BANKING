package com.oracle.banking.auth.service;

import com.oracle.banking.auth.dto.OtpVerification;
import com.oracle.banking.auth.dto.TwoFactorStatus;
import com.oracle.banking.auth.exception.TwoFactorException;
import com.oracle.banking.shared.constants.SecurityConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class TwoFactorClient {
    private final RestClient client;
    private final String twoFactorServiceUrl;
    private final String internalKey;

    public TwoFactorClient(RestClient client,
                           @Value("${services.twofa.base-url}") String twoFactorServiceUrl,
                           @Value("${services.internal-api-key}") String internalKey) {
        this.client = client;
        this.twoFactorServiceUrl = twoFactorServiceUrl;
        this.internalKey = internalKey;
    }

    public boolean isEnabled(String userId) {
        TwoFactorStatus status = client.get().uri(twoFactorServiceUrl + "/internal/twofa/users/{id}/status", userId)
                .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalKey)
                .retrieve().body(TwoFactorStatus.class);
        return status != null && status.enabled();
    }

    public void verify(String userId, String otpCode) {
        try {
            client.post().uri(twoFactorServiceUrl + "/internal/twofa/verify")
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalKey)
                    .body(new OtpVerification(userId, otpCode))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new TwoFactorException("Invalid OTP code");
            }
            throw ex;
        }
    }
}
