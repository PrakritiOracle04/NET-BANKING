package com.oracle.banking.account.service;

import com.oracle.banking.account.dto.AccountOperationsDtos.AccountPage;
import com.oracle.banking.account.dto.AccountOperationsDtos.AccountSummary;
import com.oracle.banking.account.entity.Account;
import com.oracle.banking.account.entity.AccountStatus;
import com.oracle.banking.account.repository.AccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountOperationsService {
    private final AccountRepository accounts;

    public AccountOperationsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public AccountPage search(String customerUserId, AccountStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Account> result;
        if (customerUserId != null && !customerUserId.isBlank() && status != null) {
            result = accounts.findByCustomerUserIdAndStatus(customerUserId, status, pageable);
        } else if (customerUserId != null && !customerUserId.isBlank()) {
            result = accounts.findByCustomerUserId(customerUserId, pageable);
        } else if (status != null) {
            result = accounts.findByStatus(status, pageable);
        } else {
            result = accounts.findAll(pageable);
        }
        return AccountPage.from(result);
    }

    @Transactional(readOnly = true)
    public AccountSummary summary() {
        return new AccountSummary(
                accounts.count(),
                accounts.countByStatus(AccountStatus.ACTIVE),
                accounts.countByStatus(AccountStatus.FROZEN),
                accounts.countByStatus(AccountStatus.CLOSED));
    }
}
