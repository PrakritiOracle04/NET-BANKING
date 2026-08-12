package com.oracle.banking.billpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oracle.banking.billpayment.entity.BillerCatalog;
import com.oracle.banking.billpayment.entity.BillerCategory;
import com.oracle.banking.billpayment.entity.BillerStatus;
import com.oracle.banking.billpayment.repository.BillerCatalogRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class BillerCatalogSeederTest {
    @Test
    void seedsTwoActiveBillersForEveryCategory() {
        BillerCatalogRepository repository = Mockito.mock(BillerCatalogRepository.class);
        when(repository.findByBillerCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new BillerCatalogSeeder(repository).seed();

        ArgumentCaptor<BillerCatalog> captor = ArgumentCaptor.forClass(BillerCatalog.class);
        verify(repository, times(14)).save(captor.capture());
        for (BillerCategory category : BillerCategory.values()) {
            assertThat(captor.getAllValues())
                    .filteredOn(biller -> biller.getCategory() == category)
                    .hasSize(2);
        }
    }

    @Test
    void deactivatesLegacyPlaceholderWithoutDeletingIt() {
        BillerCatalogRepository repository = Mockito.mock(BillerCatalogRepository.class);
        BillerCatalog legacy = new BillerCatalog(
                "CODEX_ELEC", "Codex City Power", BillerCategory.ELECTRICITY);
        when(repository.findByBillerCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByBillerCodeIgnoreCase("CODEX_ELEC")).thenReturn(Optional.of(legacy));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new BillerCatalogSeeder(repository).seed();

        assertThat(legacy.getStatus()).isEqualTo(BillerStatus.INACTIVE);
        verify(repository).save(legacy);
    }
}
