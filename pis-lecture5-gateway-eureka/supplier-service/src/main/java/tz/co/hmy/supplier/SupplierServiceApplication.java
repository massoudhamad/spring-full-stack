package tz.co.hmy.supplier;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Owns suppliers. With the Eureka client on the classpath it registers itself
 * as "supplier-service" (spring.application.name) on start-up: no annotation needed.
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Supplier Service", version = "v1",
        description = "Owns suppliers. Reached through the API gateway."))
public class SupplierServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplierServiceApplication.class, args);
    }
}
