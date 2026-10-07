package tz.co.hmy.supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = { "server.port=8201", "eureka.client.enabled=false" })   // no Eureka server in tests
@AutoConfigureMockMvc
class SupplierApiTest {

    @Autowired MockMvc mvc;

    @Test
    void lists_the_three_suppliers() throws Exception {
        mvc.perform(get("/api/suppliers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].id").value("S-001"));
    }

    @Test
    void returns_one_supplier_with_its_status() throws Exception {
        mvc.perform(get("/api/suppliers/S-003"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Pemba Hardware Ltd"))
            .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void an_unknown_supplier_is_a_404_problem() throws Exception {
        mvc.perform(get("/api/suppliers/S-999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Supplier not found"));
    }

    @Test
    void every_response_says_which_copy_answered() throws Exception {
        mvc.perform(get("/api/suppliers"))
            .andExpect(header().string("X-Served-By", "supplier-service:8201"));
    }
}
