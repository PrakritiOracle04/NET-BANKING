package com.oracle.banking.card.service;

import com.oracle.banking.card.dto.CardOperationsDtos.CardPage;
import com.oracle.banking.card.dto.CardOperationsDtos.CardSummary;
import com.oracle.banking.card.entity.BankCard;
import com.oracle.banking.card.entity.CardStatus;
import com.oracle.banking.card.entity.CardType;
import com.oracle.banking.card.repository.CardRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class CardOperationsService {
    private final CardRepository repository;

    public CardOperationsService(CardRepository repository) {
        this.repository = repository;
    }

    public CardPage search(String customerUserId, CardType cardType, CardStatus status, int page, int size) {
        Specification<BankCard> spec = (root, query, builder) -> builder.conjunction();
        if (customerUserId != null && !customerUserId.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("customerUserId"), customerUserId));
        }
        if (cardType != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("cardType"), cardType));
        if (status != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        return CardPage.from(repository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    public CardSummary summary() {
        return new CardSummary(repository.count(), repository.countByStatus(CardStatus.INACTIVE),
                repository.countByStatus(CardStatus.ACTIVE), repository.countByStatus(CardStatus.BLOCKED),
                repository.countByStatus(CardStatus.EXPIRED));
    }
}
