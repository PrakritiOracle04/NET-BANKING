package com.oracle.banking.auth.service;

import com.oracle.banking.auth.dto.UserOperationsDtos.UserPage;
import com.oracle.banking.auth.dto.UserOperationsDtos.UserSummary;
import com.oracle.banking.auth.repository.AppUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserOperationsService {
    private final AppUserRepository users;

    public UserOperationsService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserPage search(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return UserPage.from(status == null || status.isBlank()
                ? users.findAll(pageable)
                : users.findByStatus(status.toUpperCase(), pageable));
    }

    @Transactional(readOnly = true)
    public UserSummary summary() {
        long total = users.count();
        long active = users.countByStatus("ACTIVE");
        return new UserSummary(total, active, Math.max(0, total - active));
    }
}
