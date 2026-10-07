package tz.co.hmy.purchaseorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Owns purchase orders. Registers in Eureka as "purchase-order-service". */
@SpringBootApplication
public class PurchaseOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PurchaseOrderServiceApplication.class, args);
    }
}
