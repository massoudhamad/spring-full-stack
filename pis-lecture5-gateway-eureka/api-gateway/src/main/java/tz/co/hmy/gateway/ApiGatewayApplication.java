package tz.co.hmy.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single front door. Clients call only this, on port 8300. It looks at
 * each request's path, picks the service that owns it, asks Eureka where a
 * copy of that service runs, and forwards the request. The routes are in
 * application.yml.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
