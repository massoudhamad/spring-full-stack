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

    @Test
    void publishes_its_api_description_for_swagger() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Supplier Service"))
            .andExpect(jsonPath("$.paths['/api/suppliers']").exists());
    }

    /** Behind the gateway, the description names the GATEWAY, so Swagger's "Try it out" goes through it. */
    @Test
    void the_description_names_the_gateway_when_called_through_it() throws Exception {
        mvc.perform(get("/v3/api-docs")
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Host", "localhost:8300")
                .header("X-Forwarded-Port", "8300"))
            .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8300"));
    }
}
