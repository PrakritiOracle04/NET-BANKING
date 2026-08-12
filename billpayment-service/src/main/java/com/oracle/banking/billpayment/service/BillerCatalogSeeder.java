package com.oracle.banking.billpayment.service;

import com.oracle.banking.billpayment.entity.BillerCatalog;
import com.oracle.banking.billpayment.entity.BillerCategory;
import com.oracle.banking.billpayment.entity.BillerStatus;
import com.oracle.banking.billpayment.repository.BillerCatalogRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BillerCatalogSeeder {
    private static final List<String> LEGACY_PLACEHOLDER_CODES =
            List.of("CODEX_ELEC", "CODEX_NET", "CODEX_TEMP");

    private static final List<BillerSeed> DEFAULT_BILLERS = List.of(
            new BillerSeed("TANGEDCO", "Tamil Nadu Generation and Distribution Corporation", BillerCategory.ELECTRICITY),
            new BillerSeed("BESCOM", "Bangalore Electricity Supply Company", BillerCategory.ELECTRICITY),
            new BillerSeed("CMWSSB", "Chennai Metropolitan Water Supply and Sewerage Board", BillerCategory.WATER),
            new BillerSeed("BWSSB", "Bangalore Water Supply and Sewerage Board", BillerCategory.WATER),
            new BillerSeed("IGL", "Indraprastha Gas Limited", BillerCategory.GAS),
            new BillerSeed("MGL", "Mahanagar Gas Limited", BillerCategory.GAS),
            new BillerSeed("JIO", "Reliance Jio", BillerCategory.TELECOM),
            new BillerSeed("AIRTEL", "Bharti Airtel", BillerCategory.TELECOM),
            new BillerSeed("ACT_FIBERNET", "ACT Fibernet", BillerCategory.INTERNET),
            new BillerSeed("TATA_PLAY_FIBER", "Tata Play Fiber", BillerCategory.INTERNET),
            new BillerSeed("LIC", "Life Insurance Corporation of India", BillerCategory.INSURANCE),
            new BillerSeed("HDFC_LIFE", "HDFC Life Insurance", BillerCategory.INSURANCE),
            new BillerSeed("NHAI_FASTAG", "NHAI FASTag Recharge", BillerCategory.OTHER),
            new BillerSeed("BAJAJ_FINSERV", "Bajaj Finserv Loan Repayment", BillerCategory.OTHER));

    private final BillerCatalogRepository repository;

    public BillerCatalogSeeder(BillerCatalogRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    @Transactional
    void seed() {
        DEFAULT_BILLERS.forEach(this::upsert);
        LEGACY_PLACEHOLDER_CODES.forEach(this::deactivateLegacyPlaceholder);
    }

    private void upsert(BillerSeed seed) {
        BillerCatalog biller = repository.findByBillerCodeIgnoreCase(seed.code())
                .orElseGet(() -> new BillerCatalog(seed.code(), seed.name(), seed.category()));
        biller.updateCatalogDetails(seed.name(), seed.category(), BillerStatus.ACTIVE);
        repository.save(biller);
    }

    private void deactivateLegacyPlaceholder(String code) {
        repository.findByBillerCodeIgnoreCase(code).ifPresent(biller -> {
            biller.setStatus(BillerStatus.INACTIVE);
            repository.save(biller);
        });
    }

    private record BillerSeed(String code, String name, BillerCategory category) {}
}
