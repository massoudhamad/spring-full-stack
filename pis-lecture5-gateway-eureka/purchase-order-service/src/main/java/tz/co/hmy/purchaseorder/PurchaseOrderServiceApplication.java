package tz.co.hmy.purchaseorder;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Owns purchase orders. Registers in Eureka as "purchase-order-service". */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Purchase Order Service", version = "v1",
        description = "Owns purchase orders. Checks suppliers with supplier-service."))
public class PurchaseOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PurchaseOrderServiceApplication.class, args);
    }
}
