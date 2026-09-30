package tz.co.hmy.pis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Runs against a real PostgreSQL on the pis_test database.
 *
 *   createdb pis_test --owner=pis
 *
 * @Transactional rolls each test back, so the suite is repeatable and the
 * seeded reference data survives. MockMvc runs on the calling thread, which is
 * what makes that rollback work — it would not with a running server.
 *
 * @WithRole puts an authenticated OFFICER, with all an officer's permissions, in the security context, so these
 * tests are about the API, not about logging in. SecurityTest covers that.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithRole("OFFICER")
class SupplierApiTest {

    @Autowired MockMvc mvc;

    @Test
    void creates_a_supplier_and_returns_a_location_header() throws Exception {
        mvc.perform(post("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Unguja Stationers Ltd",
                      "tin": "555-666-777",
                      "registrationNumber": "BRELA-2022-5555",
                      "category": "GOODS",
                      "email": "sales@unguja.co.tz",
                      "phone": "+255 777 999 888"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void rejects_an_invalid_payload_with_field_level_errors() throws Exception {
        mvc.perform(post("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "", "tin": "bad-tin", "registrationNumber": "X",
                      "category": "GOODS", "email": "not-an-email" }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.errors.name").exists())
            .andExpect(jsonPath("$.errors.tin").exists())
            .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void rejects_a_duplicate_tin_with_409() throws Exception {
        mvc.perform(post("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Duplicate Co", "tin": "101-201-301",
                      "registrationNumber": "BRELA-9999-0001",
                      "category": "GOODS", "email": "a@b.co.tz" }
                    """))
            .andExpect(status().isConflict());
    }

    @Test
    void returns_404_for_an_unknown_supplier() throws Exception {
        mvc.perform(get("/api/v1/suppliers/{id}", "99999999-9999-9999-9999-999999999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void lists_seeded_suppliers_with_the_pagination_envelope() throws Exception {
        mvc.perform(get("/api/v1/suppliers?page=0&size=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void rejects_a_purchase_order_against_a_non_active_supplier() throws Exception {
        // 3333... is seeded as PENDING_APPROVAL
        mvc.perform(post("/api/v1/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "supplierId": "33333333-3333-3333-3333-333333333333",
                      "items": [ { "description": "Consultancy", "quantity": 1,
                                   "unit": "SERVICE", "unitPrice": 5000000.00 } ] }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.title").value("Business rule violated"));
    }
}
