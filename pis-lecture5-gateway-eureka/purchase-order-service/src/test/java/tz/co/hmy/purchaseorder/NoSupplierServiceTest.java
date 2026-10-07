package tz.co.hmy.purchaseorder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every copy of supplier-service is down: the registry has no instance of it.
 * The honest answer is 503, not a 500, and certainly not an order nobody checked.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")   // and no simple instances: supplier-service is unknown
@AutoConfigureMockMvc
class NoSupplierServiceTest {

    @Autowired MockMvc mvc;

    @Test
    void with_no_copy_of_supplier_service_running_an_order_is_a_503() throws Exception {
        mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"supplierId\": \"S-001\", \"item\": \"Paper\", \"quantity\": 1, \"unitPrice\": 12000 }"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.title").value("Supplier service unavailable"));
    }
}
