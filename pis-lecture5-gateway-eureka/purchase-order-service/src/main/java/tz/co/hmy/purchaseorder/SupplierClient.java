package tz.co.hmy.purchaseorder;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Talks to supplier-service. Notice the base URL: a service NAME, not a host
 * and port. Nothing in this service knows where supplier-service runs, or how
 * many copies there are.
 */
@Component
public class SupplierClient {

    private final RestClient http;

    public SupplierClient(RestClient.Builder loadBalanced) {
        this.http = loadBalanced.baseUrl("http://supplier-service").build();
    }

    /** The supplier, and which copy of supplier-service answered. Empty if it doesn't exist. */
    public Optional<SupplierLookup> find(String supplierId) {
        try {
            ResponseEntity<SupplierView> response = http.get()
                    .uri("/api/suppliers/{id}", supplierId)
                    .retrieve()
                    .toEntity(SupplierView.class);
            return Optional.of(new SupplierLookup(response.getBody(),
                    response.getHeaders().getFirst("X-Served-By")));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException | IllegalStateException | IllegalArgumentException e) {
            // RestClientException: the copy we reached failed, or couldn't be reached.
            // IllegalStateException / IllegalArgumentException: the load balancer found no
            // running copy at all. Which of the two depends on whether retries are on
            // (spring-retry on the classpath: "Service Instance cannot be null").
            throw new SupplierServiceUnavailableException(e);
        }
    }

    /** The fields this service needs from a supplier. Unknown JSON fields are ignored. */
    public record SupplierView(String id, String name, String status) {
        boolean isActive() { return "ACTIVE".equals(status); }
    }

    public record SupplierLookup(SupplierView supplier, String servedBy) { }

    public static class SupplierServiceUnavailableException extends RuntimeException {
        SupplierServiceUnavailableException(Throwable cause) {
            super("supplier-service is not available", cause);
        }
    }
}
