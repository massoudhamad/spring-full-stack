# Lecture 5 — API Gateway and Service Discovery (Eureka)

Two PIS services that find each other **by name**, through Eureka, behind one
API gateway. The lecture is in [`LECTURE.md`](LECTURE.md).

**Want to build it yourself?** [`BUILD-STEPS.md`](BUILD-STEPS.md) takes you through it one
file at a time: which file to create or edit, its full content, and a check after each part.

```
pis-lecture5-gateway-eureka/
├── discovery-server/        Eureka server                       :8761  dashboard: http://localhost:8761
├── supplier-service/        owns suppliers (run TWO copies)     :8201 and :8211
├── purchase-order-service/  owns orders; calls supplier-service :8202
├── api-gateway/             the single front door               :8300
└── smoke/smoke-lecture5.sh  checks everything end to end
```

No database: each service keeps its data in memory, so this lecture stays about
finding services. Java 17 and Maven are all you need.

## Run it: five terminals, in this order

```bash
cd discovery-server       && mvn spring-boot:run              # 1. the registry first
cd supplier-service       && mvn spring-boot:run              # 2. supplier-service, copy 1 (port 8201)
cd supplier-service       && PORT=8211 mvn spring-boot:run    # 3. supplier-service, copy 2
cd purchase-order-service && mvn spring-boot:run              # 4.
cd api-gateway            && mvn spring-boot:run              # 5.
```

Wait about 15 seconds after the last one starts, so every app has registered
and every client has fetched the registry. Then, in a sixth terminal:

```bash
bash smoke/smoke-lecture5.sh       # 12 checks, all should say PASS
```

Open http://localhost:8761 to see the Eureka dashboard.

## Tests

```bash
for app in discovery-server supplier-service purchase-order-service api-gateway; do
  (cd $app && mvn test)
done
```

17 tests. None need anything running: a stub stands in for the other services.

## Ports

| App | Port | Change it with |
| --- | --- | --- |
| discovery-server | 8761 | `server.port` in its `application.yml` |
| supplier-service | 8201 | `PORT=8211 mvn spring-boot:run` for another copy |
| purchase-order-service | 8202 | `PORT=…` |
| api-gateway | 8300 | `PORT=…` |

The Eureka address comes from `EUREKA_URL` (default `http://localhost:8761/eureka`).

The timings in each `application.yml` (5-second heartbeats, 15-second leases,
5-second caches) are for **development**, so changes show up in seconds while
you watch. Production keeps Eureka's defaults.
