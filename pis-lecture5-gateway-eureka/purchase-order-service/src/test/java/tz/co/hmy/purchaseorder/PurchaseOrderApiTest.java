package tz.co.hmy.purchaseorder;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * No Eureka in tests. Instead, Spring Cloud's SimpleDiscoveryClient maps the
 * NAME "supplier-service" to a tiny stub server started below. The code under
 * test is unchanged: it still calls http://supplier-service/..., and the load
 * balancer still resolves the name. Only the phone book is different.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class PurchaseOrderApiTest {

    private static final List<String> STUB_REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer SUPPLIER_STUB = startStub();

    @DynamicPropertySource
    static void supplierServiceIsTheStub(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.supplier-service[0].uri",
                () -> "http://localhost:" + SUPPLIER_STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        SUPPLIER_STUB.stop(0);
    }

    @Autowired MockMvc mvc;

    private static String order(String supplierId) {
        return """
                { "supplierId": "%s", "item": "Laptop", "quantity": 2, "unitPrice": 2500000.00 }
                """.formatted(supplierId);
    }

    @Test
    void an_order_for_an_active_supplier_is_created_after_asking_supplier_service() throws Exception {
        mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON).content(order("S-001")))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.supplierName").value("Kisiwa ICT Consultants"))
            .andExpect(jsonPath("$.total").value(5000000.00))
            .andExpect(jsonPath("$.checkedBy").value("supplier-stub"));

        // The name "supplier-service" really was resolved to the stub, and the stub was asked.
        assertThat(STUB_REQUESTS).contains("/api/suppliers/S-001");
    }

    @Test
    void a_suspended_supplier_is_refused() throws Exception {
        mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON).content(order("S-003")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Supplier S-003 is SUSPENDED, so it can't receive purchase orders"));
    }

    @Test
    void an_unknown_supplier_is_refused() throws Exception {
        mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON).content(order("S-999")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Unknown supplier S-999"));
    }

    @Test
    void a_failing_supplier_service_is_a_503_not_a_500() throws Exception {
        mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON).content(order("S-500")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.title").value("Supplier service unavailable"));
    }

    @Test
    void an_invalid_order_never_reaches_supplier_service() throws Exception {
        int before = STUB_REQUESTS.size();
        mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"supplierId\": \"S-001\", \"item\": \"Laptop\", \"quantity\": 0, \"unitPrice\": 1 }"))
            .andExpect(status().isBadRequest());
        assertThat(STUB_REQUESTS).hasSize(before);
    }

    @Test
    void created_orders_can_be_listed_and_fetched() throws Exception {
        String location = mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON).content(order("S-002")))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.supplierId").value("S-002"));
        mvc.perform(get("/api/purchase-orders")).andExpect(status().isOk());
        mvc.perform(get("/api/purchase-orders/PO-9999")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ the stub supplier-service

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/suppliers/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                STUB_REQUESTS.add(path);
                String id = path.substring("/api/suppliers/".length());
                String body = switch (id) {
                    case "S-001" -> "{\"id\":\"S-001\",\"name\":\"Kisiwa ICT Consultants\",\"tin\":\"101-201-301\",\"status\":\"ACTIVE\"}";
                    case "S-002" -> "{\"id\":\"S-002\",\"name\":\"Unguja Stationers Ltd\",\"tin\":\"555-666-777\",\"status\":\"ACTIVE\"}";
                    case "S-003" -> "{\"id\":\"S-003\",\"name\":\"Pemba Hardware Ltd\",\"tin\":\"888-777-666\",\"status\":\"SUSPENDED\"}";
                    default -> null;
                };
                int status = "S-500".equals(id) ? 500 : body == null ? 404 : 200;
                byte[] bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("X-Served-By", "supplier-stub");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
