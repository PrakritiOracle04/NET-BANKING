package com.oracle.banking.billpayment.service;

import com.oracle.banking.billpayment.dto.BillPaymentDtos.BillPaymentResponse;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.BillerCatalogRequest;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.BillerCatalogResponse;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.CustomerBillerRequest;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.CustomerBillerResponse;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.InternalBillerValidationResponse;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.InternalCompleteBillPaymentRequest;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.InternalCreateBillPaymentRequest;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.InternalFailBillPaymentRequest;
import com.oracle.banking.billpayment.entity.BillPayment;
import com.oracle.banking.billpayment.entity.BillPaymentStatus;
import com.oracle.banking.billpayment.entity.BillerCatalog;
import com.oracle.banking.billpayment.entity.BillerCategory;
import com.oracle.banking.billpayment.entity.BillerStatus;
import com.oracle.banking.billpayment.entity.CustomerBiller;
import com.oracle.banking.billpayment.entity.CustomerBillerStatus;
import com.oracle.banking.billpayment.exception.BillPaymentExceptions.BadRequest;
import com.oracle.banking.billpayment.exception.BillPaymentExceptions.Conflict;
import com.oracle.banking.billpayment.exception.BillPaymentExceptions.NotFound;
import com.oracle.banking.billpayment.repository.BillPaymentRepository;
import com.oracle.banking.billpayment.repository.BillerCatalogRepository;
import com.oracle.banking.billpayment.repository.CustomerBillerRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillPaymentService {
    private static final Logger log = LoggerFactory.getLogger(BillPaymentService.class);

    private final BillerCatalogRepository catalogRepository;
    private final CustomerBillerRepository customerBillerRepository;
    private final BillPaymentRepository paymentRepository;

    public BillPaymentService(
            BillerCatalogRepository catalogRepository,
            CustomerBillerRepository customerBillerRepository,
            BillPaymentRepository paymentRepository) {
        this.catalogRepository = catalogRepository;
        this.customerBillerRepository = customerBillerRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<BillerCatalogResponse> catalog(BillerCategory category) {
        List<BillerCatalog> billers = category == null
                ? catalogRepository.findByStatusOrderByBillerNameAsc(BillerStatus.ACTIVE)
                : catalogRepository.findByStatusAndCategoryOrderByBillerNameAsc(BillerStatus.ACTIVE, category);
        return billers.stream().map(BillerCatalogResponse::from).toList();
    }

    public BillerCatalogResponse activeCatalogBiller(String id) {
        return BillerCatalogResponse.from(catalogRepository.findByBillerIdAndStatus(id, BillerStatus.ACTIVE)
                .orElseThrow(() -> new NotFound("Biller not found")));
    }

    @Transactional
    public BillerCatalogResponse createCatalogBiller(BillerCatalogRequest request) {
        if (catalogRepository.existsByBillerCodeIgnoreCase(request.billerCode())) {
            throw new Conflict("Biller code already exists");
        }
        BillerCatalog biller = new BillerCatalog();
        apply(biller, request);
        BillerCatalog saved = catalogRepository.save(biller);
        log.info("Created catalog biller {}", saved.getBillerId());
        return BillerCatalogResponse.from(saved);
    }

    @Transactional
    public BillerCatalogResponse updateCatalogBiller(String id, BillerCatalogRequest request) {
        BillerCatalog biller = catalog(id);
        if (catalogRepository.existsByBillerCodeIgnoreCaseAndBillerIdNot(request.billerCode(), id)) {
            throw new Conflict("Biller code already exists");
        }
        apply(biller, request);
        log.info("Updated catalog biller {}", id);
        return BillerCatalogResponse.from(catalogRepository.save(biller));
    }

    @Transactional
    public void deactivateCatalogBiller(String id) {
        BillerCatalog biller = catalog(id);
        biller.setStatus(BillerStatus.INACTIVE);
        catalogRepository.save(biller);
        log.info("Deactivated catalog biller {}", id);
    }

    public List<CustomerBillerResponse> customerBillers(String userId) {
        return customerBillerRepository.findByCustomerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(CustomerBillerResponse::from).toList();
    }

    public CustomerBillerResponse customerBiller(String id, String userId) {
        return CustomerBillerResponse.from(ownedCustomerBiller(id, userId));
    }

    @Transactional
    public CustomerBillerResponse registerBiller(String userId, CustomerBillerRequest request) {
        BillerCatalog biller = catalogRepository.findByBillerIdAndStatus(request.billerId(), BillerStatus.ACTIVE)
                .orElseThrow(() -> new BadRequest("Biller must be active"));
        if (customerBillerRepository.existsByCustomerUserIdAndBillerBillerIdAndConsumerReferenceIgnoreCase(
                userId, request.billerId(), request.consumerReference())) {
            throw new Conflict("Biller registration already exists");
        }
        CustomerBiller registration = new CustomerBiller();
        registration.setCustomerUserId(userId);
        registration.setBiller(biller);
        registration.setConsumerReference(request.consumerReference());
        registration.setNickname(request.nickname());
        registration.setStatus(CustomerBillerStatus.ACTIVE);
        CustomerBiller saved = customerBillerRepository.save(registration);
        log.info("Registered biller {} for customer user ID {}", saved.getCustomerBillerId(), userId);
        return CustomerBillerResponse.from(saved);
    }

    @Transactional
    public CustomerBillerResponse updateCustomerBiller(String id, String userId, CustomerBillerRequest request) {
        CustomerBiller registration = ownedCustomerBiller(id, userId);
        BillerCatalog biller = catalogRepository.findByBillerIdAndStatus(request.billerId(), BillerStatus.ACTIVE)
                .orElseThrow(() -> new BadRequest("Biller must be active"));
        if (customerBillerRepository.existsByCustomerUserIdAndBillerBillerIdAndConsumerReferenceIgnoreCaseAndCustomerBillerIdNot(
                userId, request.billerId(), request.consumerReference(), id)) {
            throw new Conflict("Biller registration already exists");
        }
        registration.setBiller(biller);
        registration.setConsumerReference(request.consumerReference());
        registration.setNickname(request.nickname());
        registration.setStatus(CustomerBillerStatus.ACTIVE);
        log.info("Updated registered biller {}", id);
        return CustomerBillerResponse.from(customerBillerRepository.save(registration));
    }

    @Transactional
    public void deactivateCustomerBiller(String id, String userId) {
        CustomerBiller registration = ownedCustomerBiller(id, userId);
        registration.setStatus(CustomerBillerStatus.INACTIVE);
        customerBillerRepository.save(registration);
        log.info("Deactivated registered biller {}", id);
    }

    public InternalBillerValidationResponse validateBiller(String id, String userId) {
        CustomerBiller registration = customerBillerRepository.findByCustomerBillerIdAndCustomerUserId(id, userId)
                .orElseThrow(() -> new NotFound("Registered biller not found"));
        boolean active = registration.getStatus() == CustomerBillerStatus.ACTIVE
                && registration.getBiller().getStatus() == BillerStatus.ACTIVE;
        return new InternalBillerValidationResponse(
                registration.getCustomerBillerId(), registration.getCustomerUserId(),
                registration.getBiller().getBillerId(), registration.getBiller().getBillerName(),
                registration.getConsumerReference(), active);
    }

    @Transactional
    public BillPaymentResponse createPending(InternalCreateBillPaymentRequest request) {
        BillPayment existing = paymentRepository.findByWorkflowReference(request.workflowReference()).orElse(null);
        if (existing != null) {
            requireSamePayment(existing, request);
            return BillPaymentResponse.from(existing);
        }
        CustomerBiller registration = customerBillerRepository
                .findByCustomerBillerIdAndCustomerUserId(request.customerBillerId(), request.customerUserId())
                .orElseThrow(() -> new NotFound("Registered biller not found"));
        if (registration.getStatus() != CustomerBillerStatus.ACTIVE
                || registration.getBiller().getStatus() != BillerStatus.ACTIVE) {
            throw new BadRequest("Registered biller must be active");
        }
        BillPayment payment = new BillPayment();
        payment.setCustomerUserId(request.customerUserId());
        payment.setCustomerBiller(registration);
        payment.setBillerId(registration.getBiller().getBillerId());
        payment.setBillerName(registration.getBiller().getBillerName());
        payment.setConsumerReference(registration.getConsumerReference());
        payment.setSourceAccountId(request.sourceAccountId());
        payment.setAmount(request.amount());
        payment.setWorkflowReference(request.workflowReference());
        payment.setDescription(request.description());
        BillPayment saved = paymentRepository.save(payment);
        log.info("Created pending bill payment {} for workflow {}", saved.getBillPaymentId(), request.workflowReference());
        return BillPaymentResponse.from(saved);
    }

    @Transactional
    public BillPaymentResponse complete(String id, InternalCompleteBillPaymentRequest request) {
        BillPayment payment = payment(id);
        if (payment.getStatus() == BillPaymentStatus.SUCCESS) {
            if (!Objects.equals(payment.getTransactionId(), request.transactionId())
                    || !Objects.equals(payment.getTransactionReference(), request.transactionReference())) {
                throw new Conflict("Completed bill payment has different transaction references");
            }
            return BillPaymentResponse.from(payment);
        }
        if (payment.getStatus() != BillPaymentStatus.PENDING) {
            throw new Conflict("Bill payment cannot be completed from status " + payment.getStatus());
        }
        payment.complete(request.transactionId(), request.transactionReference());
        log.info("Completed bill payment {}", id);
        return BillPaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public BillPaymentResponse fail(String id, InternalFailBillPaymentRequest request) {
        BillPayment payment = payment(id);
        if (payment.getStatus() == BillPaymentStatus.SUCCESS) {
            throw new Conflict("Successful bill payment cannot be failed");
        }
        if (payment.getStatus() == BillPaymentStatus.FAILED) return BillPaymentResponse.from(payment);
        payment.fail(request.reason());
        log.info("Failed bill payment {}", id);
        return BillPaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public BillPaymentResponse cancel(String id, InternalFailBillPaymentRequest request) {
        BillPayment payment = payment(id);
        if (payment.getStatus() == BillPaymentStatus.CANCELLED) return BillPaymentResponse.from(payment);
        payment.cancel(request.reason());
        log.info("Cancelled bill payment {}", id);
        return BillPaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public BillPaymentResponse cancelByWorkflowReference(
            String workflowReference,
            InternalFailBillPaymentRequest request) {
        BillPayment payment = paymentRepository.findByWorkflowReference(workflowReference)
                .orElseThrow(() -> new NotFound("Bill payment not found"));
        if (payment.getStatus() == BillPaymentStatus.CANCELLED) return BillPaymentResponse.from(payment);
        payment.cancel(request.reason());
        log.info("Cancelled bill payment for workflow {}", workflowReference);
        return BillPaymentResponse.from(paymentRepository.save(payment));
    }

    public BillPaymentResponse payment(String id, String userId, boolean admin) {
        BillPayment payment = admin ? payment(id) : paymentRepository.findByBillPaymentIdAndCustomerUserId(id, userId)
                .orElseThrow(() -> new NotFound("Bill payment not found"));
        return BillPaymentResponse.from(payment);
    }

    public Page<BillPaymentResponse> history(
            String userId,
            BillPaymentStatus status,
            String sourceAccountId,
            String billerId,
            Instant from,
            Instant to,
            int page,
            int size) {
        if (size < 1 || size > 100 || page < 0) throw new BadRequest("Invalid pagination");
        Specification<BillPayment> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("customerUserId"), userId));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (sourceAccountId != null && !sourceAccountId.isBlank()) {
                predicates.add(builder.equal(root.get("sourceAccountId"), sourceAccountId));
            }
            if (billerId != null && !billerId.isBlank()) predicates.add(builder.equal(root.get("billerId"), billerId));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return paymentRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(BillPaymentResponse::from);
    }

    private void requireSamePayment(BillPayment payment, InternalCreateBillPaymentRequest request) {
        if (!Objects.equals(payment.getCustomerUserId(), request.customerUserId())
                || !Objects.equals(payment.getCustomerBiller().getCustomerBillerId(), request.customerBillerId())
                || !Objects.equals(payment.getSourceAccountId(), request.sourceAccountId())
                || payment.getAmount().compareTo(request.amount()) != 0
                || !Objects.equals(payment.getDescription(), request.description())) {
            throw new Conflict("Workflow reference was reused with a different bill payment request");
        }
    }

    private BillerCatalog catalog(String id) {
        return catalogRepository.findById(id).orElseThrow(() -> new NotFound("Biller not found"));
    }

    private CustomerBiller ownedCustomerBiller(String id, String userId) {
        return customerBillerRepository.findByCustomerBillerIdAndCustomerUserId(id, userId)
                .orElseThrow(() -> new NotFound("Registered biller not found"));
    }

    private BillPayment payment(String id) {
        return paymentRepository.findById(id).orElseThrow(() -> new NotFound("Bill payment not found"));
    }

    private void apply(BillerCatalog biller, BillerCatalogRequest request) {
        biller.setBillerCode(request.billerCode().trim().toUpperCase());
        biller.setBillerName(request.billerName().trim());
        biller.setCategory(request.category());
        biller.setStatus(request.status());
    }
}
