package tz.co.hmy.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * The gateway, for real, with no Eureka: SimpleDiscoveryClient maps both
 * service names to one stub server that answers as either service. The routes
 * in application.yml are unchanged and still say lb://supplier-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "eureka.client.enabled=false")
@AutoConfigureWebTestClient
class GatewayRoutingTest {

    private static final HttpServer STUB = startStub();

    @DynamicPropertySource
    static void bothServicesAreTheStub(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + STUB.getAddress().getPort();
        registry.add("spring.cloud.discovery.client.simple.instances.supplier-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.purchase-order-service[0].uri", () -> uri);
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @Autowired WebTestClient web;

    @Test
    void supplier_paths_go_to_supplier_service() {
        web.get().uri("/api/suppliers/S-001").exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("X-Served-By", "stub-as-supplier-service")   // the service's own header passes through
            .expectHeader().valueEquals("X-Gateway", "pis-api-gateway")              // and the gateway adds its own
            .expectBody().jsonPath("$.path").isEqualTo("/api/suppliers/S-001");      // the path is forwarded unchanged
    }

    @Test
    void purchase_order_paths_go_to_purchase_order_service_with_their_body() {
        web.post().uri("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"supplierId\":\"S-001\"}").exchange()
            .expectStatus().isCreated()
            .expectHeader().valueEquals("X-Served-By", "stub-as-purchase-order-service")
            .expectBody().jsonPath("$.received").isEqualTo("{\"supplierId\":\"S-001\"}");
    }

    @Test
    void a_path_no_route_matches_is_a_404() {
        web.get().uri("/api/invoices").exchange().expectStatus().isNotFound();
    }

    @Test
    void the_routes_can_be_inspected() {
        web.get().uri("/actuator/gateway/routes").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.route_id == 'suppliers')].uri").isEqualTo("lb://supplier-service")
            .jsonPath("$[?(@.route_id == 'purchase-orders')].uri").isEqualTo("lb://purchase-order-service");
    }

    /** One stub that answers as whichever service the path belongs to. */
    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                boolean orders = path.startsWith("/api/purchase-orders");
                String received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String body = "{\"path\":\"" + path + "\",\"received\":" + (received.isEmpty() ? "null" : "\"" + received.replace("\"", "\\\"") + "\"") + "}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("X-Served-By", orders ? "stub-as-purchase-order-service" : "stub-as-supplier-service");
                exchange.sendResponseHeaders(orders && "POST".equals(exchange.getRequestMethod()) ? 201 : 200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
