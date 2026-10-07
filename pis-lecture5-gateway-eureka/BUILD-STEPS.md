# Build Lecture 5 step by step

Build the gateway and the two services yourself, one file at a time. Every step
says **which file** to create or edit, gives its **full content** (or the exact
change), and ends with a **check** you run before going on.

- **🆕 Create**: make a new file at that path, and paste the whole block.
- **✏️ Edit**: open the file, find the first block, and replace it with the second.
- **✅ Check**: run it; don't go on until you see what it says.

The finished project is next to this file, and [`LECTURE.md`](LECTURE.md) explains
the ideas. This guide was tested by following it, mechanically, in an empty folder.

**You need:** JDK 17 and Maven (`java -version`, `mvn -v`), plus `curl` and `jq`. No database.

| Part | You build | Steps |
| --- | --- | --- |
| [0](#part-0--the-folders) | The folders | 1 |
| [A](#part-a--the-discovery-server-eureka) | discovery-server (Eureka) | 4 |
| [B](#part-b--supplier-service) | supplier-service, then a second copy | 10 |
| [C](#part-c--purchase-order-service) | purchase-order-service, which finds supplier-service by name | 10 |
| [D](#part-d--the-api-gateway) | api-gateway | 4 |
| [E](#part-e--survive-a-copy-going-away-edits) | Edits: retries, so losing a copy is invisible | 5 |
| [F](#part-f--the-tests) | The tests | 6 |
| [G](#part-g--the-smoke-test) | The smoke test | 2 |

Keep one terminal for each running app (five in the end) and one more for the checks.

---

## Part 0 · The folders

**Step 0.1 · Create the folder tree.** From wherever you keep projects:

```bash
mkdir -p gateway-eureka && cd gateway-eureka
mkdir -p discovery-server/src/main/java/tz/co/hmy/discovery discovery-server/src/main/resources \
         discovery-server/src/test/java/tz/co/hmy/discovery \
         supplier-service/src/main/java/tz/co/hmy/supplier supplier-service/src/main/resources \
         supplier-service/src/test/java/tz/co/hmy/supplier \
         purchase-order-service/src/main/java/tz/co/hmy/purchaseorder purchase-order-service/src/main/resources \
         purchase-order-service/src/test/java/tz/co/hmy/purchaseorder \
         api-gateway/src/main/java/tz/co/hmy/gateway api-gateway/src/main/resources \
         api-gateway/src/test/java/tz/co/hmy/gateway \
         smoke
```

Every path below is relative to `gateway-eureka/`.

---

## Part A · The discovery server (Eureka)

The registry every other app signs in to. It goes first, because the others look for it when they start.

**Step A.1**

**🆕 Create** `discovery-server/pom.xml`

The Spring Cloud BOM (`spring-cloud-dependencies`) chooses the version of every Spring Cloud dependency. `2025.0.3` is the release line built for Spring Boot 3.5.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.6</version>
        <relativePath/>
    </parent>

    <groupId>tz.co.hmy</groupId>
    <artifactId>discovery-server</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>PIS Discovery Server</name>
    <description>Eureka: the registry every PIS service registers in</description>

    <properties>
        <java.version>17</java.version>
        <!-- The Spring Cloud release line built for Spring Boot 3.5 -->
        <spring-cloud.version>2025.0.3</spring-cloud.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step A.2**

**🆕 Create** `discovery-server/src/main/java/tz/co/hmy/discovery/DiscoveryServerApplication.java`

`@EnableEurekaServer` turns this app into the registry.

```java
package tz.co.hmy.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * The service registry. Every other service registers here on start-up, renews
 * its lease with a heartbeat, and asks here where the others are.
 * Dashboard: http://localhost:8761
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
```

**Step A.3**

**🆕 Create** `discovery-server/src/main/resources/application.yml`

Port 8761 is Eureka's usual port. The short timings make changes visible in seconds; production keeps the defaults.

```yaml
server:
  port: 8761                     # the conventional Eureka port

spring:
  application:
    name: discovery-server

eureka:
  instance:
    hostname: localhost
  client:
    # This IS the registry: it doesn't register with itself or fetch from anyone.
    register-with-eureka: false
    fetch-registry: false
  server:
    # Development settings, so a stopped service disappears in seconds, not minutes.
    # Production keeps the defaults (self-preservation on, 60 s eviction).
    enable-self-preservation: false
    eviction-interval-timer-in-ms: 5000
    response-cache-update-interval-ms: 3000

management:
  endpoints:
    web:
      exposure:
        include: health
```

**Step A.4 · Run it** (terminal 1, leave it running)

```bash
cd discovery-server && mvn spring-boot:run
```

**✅ Check: the registry is up, and empty**

```bash
curl -s -H 'Accept: application/json' localhost:8761/eureka/apps | jq -c '.applications.application'
```

You should see `[]`: no apps yet. Open http://localhost:8761 too: the dashboard says "No instances available".

> **If not:** `Connection refused`: it's still starting (wait for `Started DiscoveryServerApplication` in terminal 1).

---

## Part B · supplier-service

Owns suppliers. Its data is in memory, so there's no database to set up.

**Step B.1**

**🆕 Create** `supplier-service/pom.xml`

The Eureka **client** starter is all it takes to register: no annotation needed.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.6</version>
        <relativePath/>
    </parent>

    <groupId>tz.co.hmy</groupId>
    <artifactId>supplier-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>PIS Supplier Service</name>
    <description>Owns suppliers. Registers in Eureka as supplier-service</description>

    <properties>
        <java.version>17</java.version>
        <!-- The Spring Cloud release line built for Spring Boot 3.5 -->
        <spring-cloud.version>2025.0.3</spring-cloud.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step B.2**

**🆕 Create** `supplier-service/src/main/resources/application.yml`

`spring.application.name` is the name it registers under, and the name others will call it by. `PORT` lets a second copy run without editing anything.

```yaml
server:
  port: ${PORT:8201}             # run a second copy with  PORT=8211 mvn spring-boot:run

spring:
  application:
    name: supplier-service       # the name it registers under, and the name others call it by

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
    registry-fetch-interval-seconds: 5        # development: notice changes in seconds
  instance:
    hostname: localhost
    instance-id: ${spring.application.name}:${server.port}   # unique per copy
    lease-renewal-interval-in-seconds: 5      # heartbeat every 5 s (default 30)
    lease-expiration-duration-in-seconds: 15  # gone after 15 s of silence (default 90)

management:
  endpoints:
    web:
      exposure:
        include: health, info
```

**Step B.3**

**🆕 Create** `supplier-service/src/main/java/tz/co/hmy/supplier/SupplierServiceApplication.java`

```java
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
```

**Step B.4**

**🆕 Create** `supplier-service/src/main/java/tz/co/hmy/supplier/Supplier.java`

```java
package tz.co.hmy.supplier;

/** A supplier. Kept in memory: this lecture is about finding services, not storing data. */
public record Supplier(String id, String name, String tin, SupplierStatus status) { }
```

**Step B.5**

**🆕 Create** `supplier-service/src/main/java/tz/co/hmy/supplier/SupplierStatus.java`

```java
package tz.co.hmy.supplier;

public enum SupplierStatus { ACTIVE, SUSPENDED }
```

**Step B.6**

**🆕 Create** `supplier-service/src/main/java/tz/co/hmy/supplier/SupplierRepository.java`

```java
package tz.co.hmy.supplier;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Three fixed suppliers, the same in every copy of this service, so it doesn't
 * matter which copy answers. (With a real database, every copy would share it.)
 */
@Repository
public class SupplierRepository {

    private final Map<String, Supplier> suppliers = new TreeMap<>(Map.of(
            "S-001", new Supplier("S-001", "Kisiwa ICT Consultants", "101-201-301", SupplierStatus.ACTIVE),
            "S-002", new Supplier("S-002", "Unguja Stationers Ltd", "555-666-777", SupplierStatus.ACTIVE),
            "S-003", new Supplier("S-003", "Pemba Hardware Ltd", "888-777-666", SupplierStatus.SUSPENDED)));

    public List<Supplier> findAll() {
        return List.copyOf(suppliers.values());
    }

    public Optional<Supplier> findById(String id) {
        return Optional.ofNullable(suppliers.get(id));
    }
}
```

**Step B.7**

**🆕 Create** `supplier-service/src/main/java/tz/co/hmy/supplier/SupplierController.java`

```java
package tz.co.hmy.supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierRepository suppliers;

    public SupplierController(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    public List<Supplier> findAll() {
        return suppliers.findAll();
    }

    @GetMapping("/{id}")
    public Supplier findById(@PathVariable String id) {
        return suppliers.findById(id).orElseThrow(() -> new SupplierNotFoundException(id));
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail notFound(SupplierNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Supplier not found");
        return problem;
    }

    static class SupplierNotFoundException extends RuntimeException {
        SupplierNotFoundException(String id) { super("No supplier with id " + id); }
    }
}
```

**Step B.8**

**🆕 Create** `supplier-service/src/main/java/tz/co/hmy/supplier/ServedByFilter.java`

Every response names the copy that answered. That's how you'll *see* load balancing later.

```java
package tz.co.hmy.supplier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stamps every response with which copy of this service answered, e.g.
 * "X-Served-By: supplier-service:8211". Two copies run in this lecture, and
 * this header is how you SEE the load balancer spreading the calls.
 */
@Component
public class ServedByFilter extends OncePerRequestFilter {

    private final String instance;

    public ServedByFilter(@Value("${spring.application.name}") String name,
                          @Value("${server.port}") String port) {
        this.instance = name + ":" + port;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Served-By", instance);
        chain.doFilter(request, response);
    }
}
```

**Step B.9 · Run the first copy** (terminal 2)

```bash
cd supplier-service && mvn spring-boot:run
```

**✅ Check: it answers, and it registered**

```bash
curl -s -D - localhost:8201/api/suppliers/S-001 | grep -iE '^(HTTP|x-served-by)|"name"'
sleep 10
curl -s -H 'Accept: application/json' localhost:8761/eureka/apps | jq -r '.applications.application[] | "\(.name): \([.instance[].instanceId] | join(", "))"'
```

You should see `200`, `X-Served-By: supplier-service:8201`, the supplier, then `SUPPLIER-SERVICE: supplier-service:8201`. Refresh the dashboard: it's listed there too.

> **If not:** Not registered: discovery-server isn't running, or give it a few more seconds.

**Step B.10 · Run a second copy** (terminal 3)

```bash
cd supplier-service && PORT=8211 mvn spring-boot:run
```

**✅ Check: two copies, one name**

```bash
sleep 10
curl -s -H 'Accept: application/json' localhost:8761/eureka/apps/SUPPLIER-SERVICE | jq -r '[.application.instance[].instanceId] | sort | join(", ")'
```

You should see `supplier-service:8201, supplier-service:8211`: same name, two instances.

---

## Part C · purchase-order-service

Owns purchase orders, and asks supplier-service whether a supplier is ACTIVE, **by name**.

**Step C.1**

**🆕 Create** `purchase-order-service/pom.xml`

(Part E adds one more dependency.)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.6</version>
        <relativePath/>
    </parent>

    <groupId>tz.co.hmy</groupId>
    <artifactId>purchase-order-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>PIS Purchase Order Service</name>
    <description>Owns purchase orders. Finds supplier-service through Eureka</description>

    <properties>
        <java.version>17</java.version>
        <!-- The Spring Cloud release line built for Spring Boot 3.5 -->
        <spring-cloud.version>2025.0.3</spring-cloud.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step C.2**

**🆕 Create** `purchase-order-service/src/main/resources/application.yml`

`cache.ttl: 5s` makes the load balancer re-read the list of copies every 5 seconds. (Part E adds retry settings.)

```yaml
server:
  port: ${PORT:8202}

spring:
  application:
    name: purchase-order-service
  cloud:
    loadbalancer:
      cache:
        ttl: 5s                  # development: re-read the instance list every 5 s (default 35 s)

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
    registry-fetch-interval-seconds: 5
  instance:
    hostname: localhost
    instance-id: ${spring.application.name}:${server.port}
    lease-renewal-interval-in-seconds: 5
    lease-expiration-duration-in-seconds: 15

management:
  endpoints:
    web:
      exposure:
        include: health, info
```

**Step C.3**

**🆕 Create** `purchase-order-service/src/main/java/tz/co/hmy/purchaseorder/PurchaseOrderServiceApplication.java`

```java
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
```

**Step C.4 · The most important file in the lecture**

**🆕 Create** `purchase-order-service/src/main/java/tz/co/hmy/purchaseorder/ClientConfig.java`

`@LoadBalanced` makes every `RestClient` built from this builder treat the host in a URL as a **service name**.

```java
package tz.co.hmy.purchaseorder;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {

    /**
     * @LoadBalanced is the whole trick. Every RestClient built from this
     * builder treats the host in a URL as a SERVICE NAME: before sending, it
     * asks Eureka for the running copies of that service, picks one (round
     * robin), and swaps in its real host and port.
     */
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
```

**Step C.5**

**🆕 Create** `purchase-order-service/src/main/java/tz/co/hmy/purchaseorder/SupplierClient.java`

Note the base URL: `http://supplier-service`, a name, not a host and port. (Part E changes the `catch` line.)

```java
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
        } catch (RestClientException | IllegalStateException e) {
            // RestClientException: the copy we reached failed, or couldn't be reached.
            // IllegalStateException: the load balancer found no running copy at all.
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
```

**Step C.6**

**🆕 Create** `purchase-order-service/src/main/java/tz/co/hmy/purchaseorder/PurchaseOrder.java`

```java
package tz.co.hmy.purchaseorder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A purchase order. It copies the supplier's name at the time of ordering: this
 * service never reads the supplier table, it asks supplier-service.
 *
 * @param checkedBy which copy of supplier-service confirmed the supplier
 */
public record PurchaseOrder(String id, String supplierId, String supplierName, String item,
                            int quantity, BigDecimal unitPrice, BigDecimal total,
                            String checkedBy, Instant createdAt) { }
```

**Step C.7**

**🆕 Create** `purchase-order-service/src/main/java/tz/co/hmy/purchaseorder/PurchaseOrderRequest.java`

```java
package tz.co.hmy.purchaseorder;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseOrderRequest(
        @NotBlank(message = "supplierId is required") String supplierId,
        @NotBlank(message = "item is required") String item,
        @Min(value = 1, message = "quantity must be at least 1") int quantity,
        @NotNull(message = "unitPrice is required") @DecimalMin(value = "0.00", message = "unitPrice cannot be negative") BigDecimal unitPrice
) { }
```

**Step C.8**

**🆕 Create** `purchase-order-service/src/main/java/tz/co/hmy/purchaseorder/PurchaseOrderService.java`

The rule that needs the other service: only an ACTIVE supplier can receive an order.

```java
package tz.co.hmy.purchaseorder;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

/** The business rule that needs the OTHER service: only an ACTIVE supplier can receive an order. */
@Service
public class PurchaseOrderService {

    private final SupplierClient suppliers;
    private final Map<String, PurchaseOrder> orders = new ConcurrentSkipListMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public PurchaseOrderService(SupplierClient suppliers) {
        this.suppliers = suppliers;
    }

    public PurchaseOrder create(PurchaseOrderRequest request) {
        SupplierClient.SupplierLookup lookup = suppliers.find(request.supplierId())
                .orElseThrow(() -> new BusinessRuleException("Unknown supplier " + request.supplierId()));
        if (!lookup.supplier().isActive()) {
            throw new BusinessRuleException("Supplier " + request.supplierId() + " is "
                    + lookup.supplier().status() + ", so it can't receive purchase orders");
        }

        String id = "PO-%04d".formatted(sequence.incrementAndGet());
        BigDecimal total = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        PurchaseOrder order = new PurchaseOrder(id, request.supplierId(), lookup.supplier().name(),
                request.item(), request.quantity(), request.unitPrice(), total,
                lookup.servedBy(), Instant.now());
        orders.put(id, order);
        return order;
    }

    public List<PurchaseOrder> findAll() {
        return List.copyOf(orders.values());
    }

    public Optional<PurchaseOrder> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    public static class BusinessRuleException extends RuntimeException {
        BusinessRuleException(String message) { super(message); }
    }
}
```

**Step C.9**

**🆕 Create** `purchase-order-service/src/main/java/tz/co/hmy/purchaseorder/PurchaseOrderController.java`

```java
package tz.co.hmy.purchaseorder;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService orders;

    public PurchaseOrderController(PurchaseOrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<PurchaseOrder> create(@Valid @RequestBody PurchaseOrderRequest request,
                                                UriComponentsBuilder uri) {
        PurchaseOrder created = orders.create(request);
        return ResponseEntity.created(uri.path("/api/purchase-orders/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    public List<PurchaseOrder> findAll() {
        return orders.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrder> findById(@PathVariable String id) {
        return ResponseEntity.of(orders.findById(id));
    }

    @ExceptionHandler(PurchaseOrderService.BusinessRuleException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ProblemDetail businessRule(PurchaseOrderService.BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated", ex.getMessage());
    }

    /** The other service is down. Say so honestly: 503, not 500, and not a made-up answer. */
    @ExceptionHandler(SupplierClient.SupplierServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ProblemDetail supplierServiceDown(SupplierClient.SupplierServiceUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Supplier service unavailable",
                "Suppliers can't be checked right now. Try again shortly.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalid(MethodArgumentNotValidException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed",
                ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
```

**Step C.10 · Run it** (terminal 4)

```bash
cd purchase-order-service && mvn spring-boot:run
```

**✅ Check: it finds supplier-service by name, and takes turns**

```bash
sleep 10
for i in 1 2; do
  curl -s -X POST localhost:8202/api/purchase-orders -H 'Content-Type: application/json' \
    -d '{"supplierId":"S-001","item":"Laptop","quantity":2,"unitPrice":2500000}' | jq -c '{id, supplierName, checkedBy}'
done
```

You should see two orders, `checkedBy` naming **different** copies (8201, then 8211, or the other way round). This call went straight to port 8202: the gateway comes next.

> **If not:** `503 Supplier service unavailable`: purchase-order-service hasn't fetched the registry yet (wait 10 s), or supplier-service isn't running.

---

## Part D · The API gateway

One front door for both services.

**Step D.1**

**🆕 Create** `api-gateway/pom.xml`

The gateway runs on WebFlux. **Never** add `spring-boot-starter-web` here: the gateway refuses to start.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.6</version>
        <relativePath/>
    </parent>

    <groupId>tz.co.hmy</groupId>
    <artifactId>api-gateway</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>PIS API Gateway</name>
    <description>The single front door: routes requests to services found in Eureka</description>

    <properties>
        <java.version>17</java.version>
        <!-- The Spring Cloud release line built for Spring Boot 3.5 -->
        <spring-cloud.version>2025.0.3</spring-cloud.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Cloud Gateway, reactive (WebFlux). Never add spring-boot-starter-web here. -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step D.2**

**🆕 Create** `api-gateway/src/main/java/tz/co/hmy/gateway/ApiGatewayApplication.java`

```java
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
```

**Step D.3**

**🆕 Create** `api-gateway/src/main/resources/application.yml`

The routes: a path pattern, and `lb://` plus a service name. (Part E adds a Retry filter.)

```yaml
server:
  port: ${PORT:8300}

spring:
  application:
    name: api-gateway
  cloud:
    loadbalancer:
      cache:
        ttl: 5s
    gateway:
      server:
        webflux:
          routes:
            # lb:// means "a service name: ask Eureka, then load-balance across its copies".
            - id: suppliers
              uri: lb://supplier-service
              predicates:
                - Path=/api/suppliers/**
            - id: purchase-orders
              uri: lb://purchase-order-service
              predicates:
                - Path=/api/purchase-orders/**
          default-filters:
            # Added to every response, so you can see a request came through the gateway.
            - AddResponseHeader=X-Gateway, pis-api-gateway

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
    registry-fetch-interval-seconds: 5
  instance:
    hostname: localhost
    instance-id: ${spring.application.name}:${server.port}
    lease-renewal-interval-in-seconds: 5
    lease-expiration-duration-in-seconds: 15

management:
  endpoint:
    gateway:
      access: read-only              # off by default: switch it on, read-only
  endpoints:
    web:
      exposure:
        include: health, gateway     # /actuator/gateway/routes lists the routes above
```

**Step D.4 · Run it** (terminal 5)

```bash
cd api-gateway && mvn spring-boot:run
```

**✅ Check: one address, both services, both copies**

```bash
sleep 10
for i in 1 2 3 4; do curl -s -D - -o /dev/null localhost:8300/api/suppliers | grep -i '^x-served-by'; done
curl -s -X POST localhost:8300/api/purchase-orders -H 'Content-Type: application/json' \
  -d '{"supplierId":"S-003","item":"Cement","quantity":10,"unitPrice":18000}' | jq -c '{status, detail}'
curl -s localhost:8300/actuator/gateway/routes | jq -c '.[] | {route_id, uri}'
```

You should see the two copies taking turns, a `422` because S-003 is SUSPENDED, and both routes with their `lb://` URIs. Four apps on the dashboard.

> **If not:** The gateway won't start with "Spring MVC found on classpath": `spring-boot-starter-web` is in its `pom.xml`.

---

## Part E · Survive a copy going away (edits)

Try this first: send requests while you stop a copy.

```bash
for i in $(seq 1 16); do curl -s -o /dev/null -w '%{http_code} ' localhost:8300/api/suppliers; sleep 0.5; done; echo
```

Run it, and while it's running press **Ctrl+C** in terminal 3 (copy 8211). For a
few seconds about half the requests fail with **500**: the gateway's load
balancer still has the stopped copy in its 5-second cache. A crash (`kill -9`)
makes it worse, about 37 seconds. Start copy 8211 again (`PORT=8211 mvn spring-boot:run`), then make the
failures invisible: **retry on the next copy**.

**Step E.1 · Retry in the gateway**

**✏️ Edit** `api-gateway/src/main/resources/application.yml`

Add a `Retry` default filter, for `GET` only: repeating a `POST` could create an order twice.

Find:

```yaml
            - AddResponseHeader=X-Gateway, pis-api-gateway
```

Replace with:

```yaml
            - AddResponseHeader=X-Gateway, pis-api-gateway
            # If a copy can't be reached, try again: the load balancer picks the NEXT copy.
            # GET only: repeating a POST could create the same purchase order twice.
            - name: Retry
              args:
                retries: 2
                methods: GET
                series: SERVER_ERROR
                exceptions: java.io.IOException, java.util.concurrent.TimeoutException
```

**Step E.2 · Retry inside purchase-order-service: the library**

**✏️ Edit** `purchase-order-service/pom.xml`

The load balancer can only retry when `spring-retry` is on the classpath.

Find:

```xml
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
```

Replace with:

```xml
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <!-- Lets the load balancer retry a failed call on the next copy of the service -->
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>
```

**Step E.3 · …and the settings**

**✏️ Edit** `purchase-order-service/src/main/resources/application.yml`

Try the next copy once. Only `GET`s are retried, and every call to supplier-service is a `GET`.

Find:

```yaml
        ttl: 5s                  # development: re-read the instance list every 5 s (default 35 s)
```

Replace with:

```yaml
        ttl: 5s                  # development: re-read the instance list every 5 s (default 35 s)
      retry:
        # With spring-retry on the classpath, a failed call to one copy of
        # supplier-service is tried once more, on the NEXT copy. Only GETs are
        # retried (retry-on-all-operations stays false), and every call to
        # supplier-service is a GET.
        max-retries-on-same-service-instance: 0
        max-retries-on-next-service-instance: 1
```

**Step E.4 · Report "no copy at all" as 503, with retries on**

**✏️ Edit** `purchase-order-service/src/main/java/tz/co/hmy/purchaseorder/SupplierClient.java`

With `spring-retry` on, the load balancer reports "no copy found" as an `IllegalArgumentException`. Without this edit, an order would get a **500** when every copy is down, instead of an honest **503**.

Find:

```java
        } catch (RestClientException | IllegalStateException e) {
            // RestClientException: the copy we reached failed, or couldn't be reached.
            // IllegalStateException: the load balancer found no running copy at all.
            throw new SupplierServiceUnavailableException(e);
        }
```

Replace with:

```java
        } catch (RestClientException | IllegalStateException | IllegalArgumentException e) {
            // RestClientException: the copy we reached failed, or couldn't be reached.
            // IllegalStateException / IllegalArgumentException: the load balancer found no
            // running copy at all. Which of the two depends on whether retries are on
            // (spring-retry on the classpath: "Service Instance cannot be null").
            throw new SupplierServiceUnavailableException(e);
        }
```

**Step E.5 · Restart, and repeat the experiment**

Stop the gateway (terminal 5) and purchase-order-service (terminal 4) with
Ctrl+C, and start both again (`mvn spring-boot:run`). Wait 10 seconds.

**✅ Check: stopping a copy is invisible now**

```bash
for i in $(seq 1 16); do curl -s -o /dev/null -w '%{http_code} ' localhost:8300/api/suppliers; sleep 0.5; done; echo
```

You should see all `200`, even when you press Ctrl+C in terminal 3 while it runs. Then, with **both** supplier copies stopped, `curl -s -X POST localhost:8300/api/purchase-orders -H 'Content-Type: application/json' -d '{"supplierId":"S-001","item":"Paper","quantity":1,"unitPrice":12000}' | jq -c '{status, title}'` gives `503 "Supplier service unavailable"`. Start both copies again before Part G.

> **If not:** Some `500`s: the `Retry` block isn't under `default-filters`, or the gateway wasn't restarted.

---

## Part F · The tests

None of these need anything running: a stub server stands in for the other services.

**Step F.1**

**🆕 Create** `discovery-server/src/test/java/tz/co/hmy/discovery/DiscoveryServerTest.java`

```java
package tz.co.hmy.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerTest {

    @Autowired TestRestTemplate http;

    @Test
    void the_registry_answers_and_starts_empty() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> apps = http.exchange("/eureka/apps", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(apps.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apps.getBody()).contains("\"applications\"").doesNotContain("\"instance\"");
    }

    @Test
    void the_dashboard_is_served() {
        ResponseEntity<String> page = http.getForEntity("/", String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Eureka");
    }
}
```

**Step F.2**

**🆕 Create** `supplier-service/src/test/java/tz/co/hmy/supplier/SupplierApiTest.java`

```java
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
```

**Step F.3**

**🆕 Create** `purchase-order-service/src/test/java/tz/co/hmy/purchaseorder/PurchaseOrderApiTest.java`

`SimpleDiscoveryClient` maps the name `supplier-service` to a stub the test starts. The code under test still calls `http://supplier-service/...`.

```java
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
```

**Step F.4**

**🆕 Create** `purchase-order-service/src/test/java/tz/co/hmy/purchaseorder/NoSupplierServiceTest.java`

This test is why Step E.4 exists: with no copy registered, the answer must be 503.

```java
package tz.co.hmy.purchaseorder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every copy of supplier-service is down: the registry has no instance of it.
 * The honest answer is 503, not a 500, and certainly not an order nobody checked.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")   // and no simple instances: supplier-service is unknown
@AutoConfigureMockMvc
class NoSupplierServiceTest {

    @Autowired MockMvc mvc;

    @Test
    void with_no_copy_of_supplier_service_running_an_order_is_a_503() throws Exception {
        mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"supplierId\": \"S-001\", \"item\": \"Paper\", \"quantity\": 1, \"unitPrice\": 12000 }"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.title").value("Supplier service unavailable"));
    }
}
```

**Step F.5**

**🆕 Create** `api-gateway/src/test/java/tz/co/hmy/gateway/GatewayRoutingTest.java`

```java
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
```

**Step F.6 · Run them all**

**✅ Check: 17 tests pass**

```bash
for app in discovery-server supplier-service purchase-order-service api-gateway; do
  (cd $app && mvn -q test > /dev/null && echo "$app: all tests pass" || echo "$app: TESTS FAILED")
done
```

You should see four lines ending `all tests pass` (2 + 4 + 7 + 4 = 17 tests). Lines saying `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes` are harmless: the test tools attach to the JVM.

> **If not:** Don't put an `application.yml` in `src/test/resources`: it **replaces** the main one, and the tests lose `spring.application.name`.

---

## Part G · The smoke test

**Step G.1**

**🆕 Create** `smoke/smoke-lecture5.sh`

Checks everything at once, with all five apps running.

```bash
#!/usr/bin/env bash
# Lecture 5 smoke test: Eureka, two services that find each other, and the API gateway.
# Needs all five running: discovery-server, supplier-service on 8201 AND 8211,
# purchase-order-service, api-gateway (see the README).
EUREKA=${EUREKA:-http://localhost:8761}
GATEWAY=${GATEWAY:-http://localhost:8300}
JSON='Content-Type: application/json'

pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; }
check() {   # check <expected status> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got; got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  [ "$got" = "$want" ] && echo "PASS  $got  $desc" || echo "FAIL  $got  $desc (expected $want)"
}
instances() { curl -s -H 'Accept: application/json' "$EUREKA/eureka/apps/$1" | grep -o '"instanceId":"[^"]*"' | cut -d'"' -f4 | sort | tr '\n' ' '; }
served_by() { curl -s -D - -o /dev/null "$@" | tr -d '\r' | awk -F': ' 'tolower($1)=="x-served-by"{print $2}'; }
order() { curl -s -X POST -H "$JSON" -d "{\"supplierId\":\"$1\",\"item\":\"Laptop\",\"quantity\":2,\"unitPrice\":2500000.00}" "$GATEWAY/api/purchase-orders"; }

echo "--- Eureka: who is registered?"
s=$(instances SUPPLIER-SERVICE); [ "$(echo $s | wc -w)" -ge 2 ] && pass "     supplier-service: $s" || fail "     supplier-service needs 2 copies (8201 and 8211), found: $s"
p=$(instances PURCHASE-ORDER-SERVICE); [ -n "$p" ] && pass "     purchase-order-service: $p" || fail "     purchase-order-service is not registered"
g=$(instances API-GATEWAY); [ -n "$g" ] && pass "     api-gateway: $g" || fail "     api-gateway is not registered"

echo "--- the gateway routes by path"
r=$(curl -s "$GATEWAY/actuator/gateway/routes" | grep -o '"uri":"lb://[^"]*"' | cut -d'"' -f4 | tr '\n' ' ')
echo "$r" | grep -q 'lb://supplier-service' && echo "$r" | grep -q 'lb://purchase-order-service' && pass "     routes: $r" || fail "     routes: $r"
check 200 "GET /api/suppliers through the gateway"              "$GATEWAY/api/suppliers"
h=$(curl -s -D - -o /dev/null "$GATEWAY/api/suppliers/S-001" | tr -d '\r' | grep -i '^x-gateway:')
[ -n "$h" ] && pass "     the gateway marks its responses ($h)" || fail "     no X-Gateway header"
check 404 "a path no route matches"                             "$GATEWAY/api/invoices"

echo "--- load balancing: the gateway spreads calls over both copies"
seen=$(for i in 1 2 3 4 5 6; do served_by "$GATEWAY/api/suppliers"; done | sort -u | tr '\n' ' ')
[ "$(echo $seen | wc -w)" -ge 2 ] && pass "     6 calls answered by: $seen" || fail "     6 calls answered only by: $seen"

echo "--- service to service: purchase-order-service finds supplier-service BY NAME"
o1=$(order S-001); o2=$(order S-001)
echo "$o1" | grep -q '"supplierName":"Kisiwa ICT Consultants"' && pass "201  order created after asking supplier-service" || fail "     order: $o1"
c1=$(echo "$o1" | grep -o '"checkedBy":"[^"]*"' | cut -d'"' -f4); c2=$(echo "$o2" | grep -o '"checkedBy":"[^"]*"' | cut -d'"' -f4)
[ -n "$c1" ] && [ "$c1" != "$c2" ] && pass "     two orders were checked by two copies: $c1, $c2" || fail "     both orders checked by: $c1 $c2"
check 422 "a SUSPENDED supplier is refused (rule needs the other service)" -X POST -H "$JSON" \
      -d '{"supplierId":"S-003","item":"Cement","quantity":10,"unitPrice":18000}' "$GATEWAY/api/purchase-orders"
check 422 "an unknown supplier is refused"                       -X POST -H "$JSON" \
      -d '{"supplierId":"S-999","item":"Cement","quantity":10,"unitPrice":18000}' "$GATEWAY/api/purchase-orders"
```

**Step G.2**

**✅ Check: everything, end to end**

```bash
bash smoke/smoke-lecture5.sh
```

You should see twelve lines, every one starting with `PASS`.

> **If not:** Read the first `FAIL` line: it names the step. Then compare your file with the finished project next to this guide.

---

You've built it. [`LECTURE.md`](LECTURE.md) has the concepts behind every step,
the measured failure results, common mistakes, exercises and a quiz.
