package tz.co.hmy.supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Owns suppliers. With the Eureka client on the classpath it registers itself
 * as "supplier-service" (spring.application.name) on start-up: no annotation needed.
 */
@SpringBootApplication
public class SupplierServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplierServiceApplication.class, args);
    }
}
