package tz.co.hmy.pis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.*;
import tz.co.hmy.pis.repository.InvoiceRepository;
import tz.co.hmy.pis.repository.SupplierRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoiceRepositoryTest {

    @Autowired InvoiceRepository invoices;
    @Autowired SupplierRepository suppliers;

    private Invoice persistInvoice(String number) {
        Supplier s = suppliers.save(Supplier.builder()
                .name("Repo Test " + number).tin("111-222-" + number.substring(number.length() - 3))
                .registrationNumber("REG-" + number).category(SupplierCategory.GOODS)
                .email(number + "@example.com").build());

        Invoice inv = Invoice.builder()
                .invoiceNumber(number).invoiceDate(LocalDate.now()).supplier(s).build();
        inv.addItem(InvoiceItem.builder()
                .description("Line one").quantity(2).unit(UnitOfMeasure.PIECE)
                .unitPrice(new BigDecimal("1500.00")).build());
        return invoices.save(inv);
    }

    @Test
    void saves_and_reads_back_with_items() {
        Invoice saved = persistInvoice("INV-T-001");
        Invoice found = invoices.findWithItemsById(saved.getId()).orElseThrow();
        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getSupplier().getName()).startsWith("Repo Test");
        assertThat(found.getTotal()).isEqualByComparingTo("3000.00");
    }

    @Test
    void detects_a_duplicate_invoice_number() {
        persistInvoice("INV-T-002");
        assertThat(invoices.existsByInvoiceNumber("INV-T-002")).isTrue();
        assertThat(invoices.existsByInvoiceNumber("INV-T-999")).isFalse();
    }

    @Test
    void lists_and_deletes() {
        Invoice saved = persistInvoice("INV-T-003");
        assertThat(invoices.findAll(PageRequest.of(0, 20)).getTotalElements()).isGreaterThanOrEqualTo(1);
        invoices.delete(saved);
        assertThat(invoices.findById(saved.getId())).isEmpty();
    }
}
