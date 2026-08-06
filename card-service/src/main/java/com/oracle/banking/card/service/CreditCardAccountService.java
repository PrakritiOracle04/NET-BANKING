package com.oracle.banking.card.service;

import com.oracle.banking.card.dto.CardDtos.CreditCardAccountResponse;
import com.oracle.banking.card.entity.CardProduct;
import com.oracle.banking.card.entity.CreditCardAccount;
import com.oracle.banking.card.exception.CardExceptions.Conflict;
import com.oracle.banking.card.exception.CardExceptions.NotFound;
import com.oracle.banking.card.repository.CreditCardAccountRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditCardAccountService {
    private final CreditCardAccountRepository repository;

    public CreditCardAccountService(CreditCardAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreditCardAccountResponse create(
            String cardId,
            String customerUserId,
            String linkedAccountId,
            CardProduct cardProduct,
            BigDecimal creditLimit,
            int billingCycleDay) {
        if (repository.existsByCardId(cardId)) {
            throw new Conflict("Credit account already exists for this card");
        }
        CreditCardAccount account = new CreditCardAccount();
        account.setCardId(cardId);
        account.setCustomerUserId(customerUserId);
        account.setLinkedAccountId(linkedAccountId);
        account.setCardProduct(cardProduct);
        account.setCreditLimit(creditLimit);
        account.setAvailableCredit(creditLimit);
        account.setOutstandingBalance(BigDecimal.ZERO);
        account.setBillingCycleDay(billingCycleDay);
        return CreditCardAccountResponse.from(repository.save(account));
    }

    public List<CreditCardAccountResponse> accounts(String userId, boolean admin, String customerUserId) {
        if (admin && customerUserId == null) {
            return repository.findAllByOrderByCreatedAtDesc().stream().map(CreditCardAccountResponse::from).toList();
        }
        String owner = admin && customerUserId != null ? customerUserId : userId;
        return repository.findByCustomerUserIdOrderByCreatedAtDesc(owner).stream().map(CreditCardAccountResponse::from).toList();
    }

    public CreditCardAccountResponse byCard(String cardId, String userId, boolean admin) {
        CreditCardAccount account = admin
                ? repository.findByCardId(cardId).orElseThrow(() -> new NotFound("Credit card account not found"))
                : repository.findByCardIdAndCustomerUserId(cardId, userId)
                .orElseThrow(() -> new NotFound("Credit card account not found"));
        return CreditCardAccountResponse.from(account);
    }
}
