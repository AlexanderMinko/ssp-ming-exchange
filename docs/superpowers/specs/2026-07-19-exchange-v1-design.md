# Exchange v1 — Design Spec

Date: 2026-07-19
Status: approved for planning
Service: `exchange` (this repo, `com.ming.sspexchange`), first of three simulator components (exchange → dsp → ssp).

## 1. Purpose

Minimal SSP/Exchange simulator: one HTTP endpoint that accepts an OpenRTB 2.6 bid request, resolves the supply partner from MongoDB-backed config, fans out to eligible demand partners (bidders), runs a first-price auction, and returns a single-seat bid response. Deployed to EKS `eks-dev` (eu-central-1). Synthetic data only.

## 2. Stack decisions (fixed)

- Spring Boot 4.1, Java 25, **blocking MVC on virtual threads** (`spring.threads.virtual.enabled=true`). No WebFlux.
- Outbound HTTP: `RestClient`.
- **dsl-json 2.0.2** for the entire ORTB hot path (parse + serialize), wired the same way as the commercial ssp-backend: a custom `HttpMessageConverter` + typed controllers (see §10). Jackson remains on the classpath only for actuator/Spring internals — it must not touch bid payloads.
- MongoDB via `spring-boot-starter-data-mongodb`, database name **`ssp-exchange`**.
- No structured-concurrency preview APIs; plain `CompletableFuture` on a virtual-thread executor.
- Code convention: null checks via `java.util.Objects.isNull` / `Objects.nonNull` — never bare `== null` / `!= null`.

## 3. HTTP contract

`POST /rtb/bid?account={accountKey}` — OpenRTB 2.6 JSON body.

| Result | Response |
|---|---|
| Fill | `200` + `BidResponse` (single seatbid, single bid) |
| No-bid: no eligible bidders, no bids returned, all bids under floor, all bidders timed out | `204`, empty body |
| Malformed JSON / failed ORTB validation | `400` |
| Unknown `accountKey`, or publisher id from body not registered under that account, or account/publisher `status != ACTIVE` | `403` |

Supply identification: `accountKey` from the query param → Account. Publisher external id read from the body (`app.publisher.id` if `app` present, else `site.publisher.id`) → Publisher looked up under that Account.

Validation rules (400 on failure): `id` present; ≥ 1 `imp`; every imp has `banner` or `video`; `imp.bidfloor` if present is a valid non-negative number; `app` xor `site` present with a `publisher.id`.

## 4. Mongo data model

Database `ssp-exchange`, three collections. Config-only — no auction data is written to Mongo.

### `accounts`
```
{ _id, accountKey (unique, used in URL param), name, type: "SDK"|"API", status: "ACTIVE"|"PAUSED" }
```

### `publishers`
```
{ _id, accountId (ref accounts._id), publisherId (external id matched against request body), name, status }
```
Uniqueness: `(accountId, publisherId)`.

### `bidders`
```
{ _id, name, seat, endpoint (absolute URL, or "mock:<name>" for built-in mock demand), status,
  targeting: { formats: ["BANNER","VIDEO"], countries: [] },   // empty countries = all
  tmaxMs (optional per-bidder override),
  mock (required when endpoint is mock:*): { price, latencyMs (opt, default 0), bidRate (opt 0.0–1.0, default 1.0) } }
```

**Mock demand (until the dummy DSP exists):** a bidder whose `endpoint` starts with `mock:` is served in-process by a `MockBidderClient` instead of HTTP — it sleeps `latencyMs` (virtual thread; still subject to the fan-out deadline, so latency > timeout genuinely produces TIMEOUT), bids with probability `bidRate`, and fabricates a single-bid `BidResponse` at the fixed `price` with macro placeholders in `adm`/`nurl`. Everything downstream (targeting, auction, floors, macros, event log, metrics) treats mock and HTTP bidders identically. Deploying the real DSP later = updating `endpoint` in Mongo; zero code changes.

Seeding: JSON fixtures in `src/main/resources/seed/` (also used by tests), loaded via `scripts/seed.sh` (mongosh). Fixtures: 2 accounts, 3 publishers, 3 bidders (distinct targeting so filtering is demonstrable).

## 5. Config cache

- `PartnerConfigCache`: loads all three collections at startup (fail fast if Mongo is unreachable), `@Scheduled` full refresh every 30 s.
- Structures: `Map<accountKey, Account>`, `Map<accountId, Map<publisherId, Publisher>>`, `List<Bidder>` — replaced atomically per refresh (volatile snapshot object holding all three; readers never see a partial refresh).
- Refresh failure: log + keep last-good snapshot.
- Spring Data repositories are used by the cache loader only; nothing else touches Mongo at request time.

## 6. Request pipeline

Mini-layered: one small class per stage, wired by the controller. No pipeline framework.

```
BidController (typed: @RequestBody BidRequest → ResponseEntity<BidResponse>; dsl-json converter does the codec work, parse failure → 400 via exception handler)
 → SupplyResolver       accountKey → Account, body → Publisher; 403 on failure
 → RequestValidator     rules from §3; 400 on failure
 → BidderTargetingFilter status ACTIVE + format match + country match (device.geo.country, ISO-3166-1 alpha-3; a request with no country matches all bidders — the country filter applies only when the request carries one)
 → BidRequestForwarder  builds per-bidder outbound request (see §7)
 → FanOutService        parallel calls with a single auction deadline; per-bidder dispatch:
                         endpoint mock:* → in-process MockBidderClient, else HTTP BidderClient
 → WinnerSelector       first-price winner (see §8)
 → ResponseBuilder      single-seat BidResponse + adm/nurl macro substitution
 → async after response: AuctionEventLogger (always)
```

Format derivation per request: union over imps (`banner` present → BANNER, `video` → VIDEO). A bidder is eligible if its `targeting.formats` intersects the request's formats.

## 7. Outbound requests, concurrency, tmax

- `effectiveTmax = clamp(request.tmax ?? 300, 100, 1000)` ms. Outbound `tmax = effectiveTmax − 50` (overhead reserve). Per-bidder `tmaxMs` override, if lower, wins for that bidder's request + timeout.
- Outbound body: the parsed request re-serialized with dsl-json, with `tmax` overwritten. **Fields not covered by the model are dropped** (dsl-json compiled mode has no unknown-field capture); declared `ext` maps survive. Accepted for the simulator — mirrors an exchange rebuilding requests.
- Fan-out: one `CompletableFuture.supplyAsync` per eligible bidder on a shared `Executors.newVirtualThreadPerTaskExecutor()` bean; gather with a single deadline of `effectiveTmax` (`orTimeout`/timed join). Late, failed, non-200, or unparseable bidders are dropped; each per-bidder outcome recorded as `BID | NO_BID(204) | TIMEOUT | ERROR` with latency for the event log + metrics.
- Outbound client (prod pattern): dedicated JDK `HttpClient` bean — HTTP/2, `Executors.newVirtualThreadPerTaskExecutor()`, connect timeout 100 ms — wrapped in `JdkClientHttpRequestFactory` with read timeout 1000 ms (hard cap); the real per-auction bound is the future deadline. `RestClient` built on that factory with converters cleared and replaced by the single `DslJsonHttpMessageConverter`, default `Content-Type`/`Accept: application/json` headers.
- Failure isolation: one bidder's exception never affects others or the caller.

## 8. Auction: winner, macros, nurl

- Candidate bids: flatten all `seatbid[].bid[]`; keep bids where `impid` equals the **first imp's id** (v1 awards only the first imp — multi-imp is out of scope), `price ≥ imp.bidfloor` (default 0), `adm` non-empty.
- **First-price**: winner = max price (tie → first received); clearing price = bid price.
- Macros substituted in `adm` and `nurl`: `${AUCTION_ID}`, `${AUCTION_BID_ID}`, `${AUCTION_IMP_ID}`, `${AUCTION_SEAT_ID}`, `${AUCTION_PRICE}`.
- Response: `id` = request id, `cur` = "USD", one `seatbid` with the winning bidder's `seat`, one `bid`.
- `nurl`: the exchange does **not** fire it. It is returned macro-substituted inside the winning bid; the supply-partner SSP (later phase) triggers it on win.
- No loss notices, no `burl`.

## 9. Event log & observability

- **Auction event log**: exactly one structured JSON line per auction on logger `AUCTION_EVENTS` (event record serialized with dsl-json via `DslJsonSerializer`, logged through a dedicated `%msg%n` appender — no logstash encoder needed): auctionId, accountKey, publisherId, requested formats, eligible bidder names, per-bidder `{name, outcome, latencyMs, price?}`, winner + clearing price, response status, total latency. Loki-ready; not stored in Mongo.
- **Metrics** (Micrometer → OTLP via `micrometer-registry-otlp`): counters `auctions_total`, `nobid_total{reason}`, `bidder_requests_total{bidder,outcome}`, `wins_total{bidder}`; timers `auction_duration`, `bidder_call_duration{bidder}`.
- **Traces**: `micrometer-tracing-bridge-otel` + OTLP span exporter; server span per auction, child span per bidder call.
- Exporters must degrade silently when no collector is reachable (local dev).

## 10. dsl-json specifics (for the executor — mirror of the commercial ssp-backend wiring)

- **Models**: ORTB classes annotated `@CompiledJson` + Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`, private fields, wrapper types (`Integer`, `Double`) so absent ≠ 0. Pure ORTB wire-shape mirrors — no business fields. Every object carries `private Map<String, Object> ext;`.
- Classes: `BidRequest, Imp, Banner, Video, Site, App, Publisher, Device, Geo, User, Source, Regs` / `BidResponse, SeatBid, Bid`. Only fields the simulator touches + `ext`.
- **DslJson bean** (singleton):
  ```java
  new DslJson<>(Settings.withRuntime()
      .allowArrayFormat(true)
      .skipDefaultValues(true)   // write-only: omits null/default fields from serialized output
      .includeServiceLoader());
  ```
- **Server side**: `DslJsonHttpMessageConverter extends AbstractHttpMessageConverter<Object>` — `supports`/`canRead`/`canWrite` delegate to `dslJson.canDeserialize`/`canSerialize`; `readInternal` → `dslJson.deserialize(type, body)` wrapping `IOException` in `HttpMessageNotReadableException`; `writeInternal` → `dslJson.serialize(object, body)`. Registered via `WebMvcConfigurer.configureMessageConverters(ServerBuilder builder) { builder.addCustomConverter(...) }` (Boot 4 API). Controllers stay typed (`@RequestBody BidRequest`, `ResponseEntity<BidResponse>`).
- **Client side**: same converter instance type installed as the *only* converter on the bidder `RestClient` (§7).
- **Build**: `com.dslplatform:dsl-json:2.0.2` as a normal dependency **and** as an entry in `maven-compiler-plugin` `annotationProcessorPaths`, ordered after Lombok (2.x ships the processor in the main artifact — no separate processor jar).
- Utility `DslJsonSerializer` component (`serialize(T) → byte[]` via `ByteArrayOutputStream`) for places needing raw bytes (e.g. event logging).
- Required unit test: round-trip a full sample 2.6 request fixture (deserialize → serialize → re-deserialize, assert stable; assert nulls omitted on write).

## 11. Package layout

```
com.ming.sspexchange
├── api            BidController, GlobalExceptionHandler
├── config         DslJson bean, DslJsonHttpMessageConverter, WebMvcConfig, bidder HttpClient/RestClient, virtual-thread executor bean, OTLP, @ConfigurationProperties
├── model
│   ├── openrtb    dsl-json classes (§10)
│   └── entity     AccountEntity, PublisherEntity, BidderEntity (Spring Data)
├── repository     AccountRepository, PublisherRepository, BidderRepository
├── cache          PartnerConfigCache (+ snapshot record, refresher)
└── service
    ├── supply     SupplyResolver, RequestValidator
    ├── demand     BidderTargetingFilter, BidRequestForwarder, FanOutService, BidderClient
    ├── auction    WinnerSelector, ResponseBuilder, MacroProcessor
    └── event      AuctionEventLogger
```

## 12. pom.xml changes

- Remove: `spring-boot-starter-mongodb`, `spring-boot-starter-mongodb-test` (driver-only starters, redundant next to `data-mongodb`).
- Keep: `webmvc`, `restclient`, `data-mongodb` + their test starters, Lombok (used on ORTB DTOs too, per §10).
- Add: `spring-boot-starter-actuator`, `com.dslplatform:dsl-json:2.0.2` (dependency **and** `annotationProcessorPaths` entry after Lombok), `micrometer-registry-otlp`, `micrometer-tracing-bridge-otel`, `io.opentelemetry:opentelemetry-exporter-otlp`; test: `spring-boot-testcontainers`, `org.testcontainers:mongodb` + `junit-jupiter`, WireMock (`org.wiremock`, artifact compatible with Boot 4 / Jetty 12).
- `application.properties` → `application.yaml`; `spring.threads.virtual.enabled=true`; Mongo URI + db `ssp-exchange`; OTLP endpoints via env-overridable properties.

## 13. Testing

- **Unit**: WinnerSelector (floor filter, tie, empty, wrong impid), BidderTargetingFilter (format/country/status matrix, missing geo), MacroProcessor, tmax clamping, SupplyResolver (403 cases), MockBidderClient (bid/no-config/bidRate-0/fabricated fields; latency-beyond-deadline → TIMEOUT via fan-out), dsl-json round-trip (§10).
- **Integration** (`@SpringBootTest` + Testcontainers Mongo + WireMock): two stubbed bidders — one bids, one delays past tmax → assert 200 + winner correctness + macro-substituted `adm`/`nurl` in the returned bid; mock-only demand → 200 without any HTTP bidder; no-bidders-bid → 204; bad account → 403; malformed body → 400. Seed fixtures reused from `src/main/resources/seed/`.
- User runs all builds/tests: plan must hand over exact `./mvnw` commands.

## 14. Deployment

- **Docker**: multi-stage (JDK 25 build → JRE 25 runtime), non-root. Push to ECR `140023370575.dkr.ecr.eu-central-1.amazonaws.com/eks-dev/app`, tag `exchange-<version>`.
- **K8s manifests: deferred** — authored later by the user. Decisions that stand: namespace `production` for this service (ssp/dsp get their own later), edge via Gateway API `HTTPRoute`, in-cluster single-replica MongoDB, health probes on actuator endpoints.
- **Local dev**: `docker-compose.yml` (mongo + auto-seed container); app via `./mvnw spring-boot:run`. Seed ships mock bidders, so the exchange fills (200) from the very first run — no DSP required.
- User executes all build/deploy commands; plan provides docker build/push commands.

## 15. Out of scope (v1)

Second-price, deals/PMP, server-side win-notice firing (`nurl` returned in the bid; supply SSP triggers it later), loss notices (`lurl`), `burl`/billing, IVT, privacy enforcement (TCF/GPP strings pass through `ext`/`regs` untouched), currency conversion (USD only), multi-imp awards (first imp only), admin CRUD API, Kafka/eventing, user matching/cookie sync, K8s manifests (user authors them later).

## 16. Sequencing after v1

1. Dummy DSP service (own repo/namespace): configurable bid behavior (price ranges, no-bid rate, latency jitter) — becomes the target of `bidders.endpoint`, replacing the built-in `mock:` bidders via a Mongo config update only.
2. Dummy supply SSP: traffic generator posting synthetic 2.6 requests at the exchange.
3. Wire end-to-end across namespaces; then LGTM dashboards off the OTLP/AUCTION_EVENTS data.
