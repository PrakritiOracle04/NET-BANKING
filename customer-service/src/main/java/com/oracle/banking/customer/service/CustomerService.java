package com.oracle.banking.customer.service;

import com.oracle.banking.customer.dto.CustomerDtos.Create;
import com.oracle.banking.customer.dto.CustomerDtos.Response;
import com.oracle.banking.customer.dto.CustomerDtos.Update;
import com.oracle.banking.customer.entity.CustomerProfile;
import com.oracle.banking.customer.exception.CustomerExceptions.Duplicate;
import com.oracle.banking.customer.exception.CustomerExceptions.NotFound;
import com.oracle.banking.customer.repository.CustomerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private final CustomerProfileRepository profiles;

    public CustomerService(CustomerProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional
    public Response create(Create request) {
        if (profiles.existsByUserId(request.userId())) {
            throw new Duplicate("Customer profile already exists");
        }
        return Response.from(profiles.save(new CustomerProfile(request.userId(), request.fullName())));
    }

    public Response own(String userId) {
        return Response.from(requiredByUserId(userId));
    }

    @Transactional
    public Response update(String userId, Update request) {
        CustomerProfile profile = requiredByUserId(userId);
        profile.update(request);
        return Response.from(profile);
    }

    public Response byId(String customerId) {
        return Response.from(profiles.findById(customerId)
                .orElseThrow(() -> new NotFound("Customer profile not found")));
    }

    CustomerProfile requiredByUserId(String userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> new NotFound("Customer profile not found"));
    }
}
