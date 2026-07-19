# Exchange v1 — Design Spec

Date: 2026-07-19
Status: approved for planning
Service: `exchange` (this repo, `com.ming.sspexchange`), first of three simulator components (exchange → dsp → ssp).

## 1. Purpose

Minimal SSP/Exchange simulator: one HTTP endpoint that accepts an OpenRTB 2.6 bid request, resolves the supply partner from MongoDB-backed config, fans out to eligible demand partners (bidders), runs a first-price auction, and returns a single-seat bid response. Deployed to EKS `eks-dev` (eu-central-1). Synthetic data only.

## 2. Stack decisions (fixed)

- Spring Boot 4.1, Java 25, **blocking MVC on virtual threads** (`spring.threads.virtual.enabled=true`). No WebFlux.
- Outbound HTTP: `RestClient`.
- **dsl-json 2.x** for the entire ORTB hot path (parse + serialize). Jackson remains on the classpath only for actuator/Spring internals and the logstash log encoder — it must not touch bid payloads.
- MongoDB via `spring-boot-starter-data-mongodb`, database name **`ssp-exchange`**.
- No structured-concurrency preview APIs; plain `CompletableFuture` on a virtual-thread executor.

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
{ _id, name, seat, endpoint (absolute URL), status,
  targeting: { formats: ["BANNER","VIDEO"], countries: [] },   // empty countries = all
  tmaxMs (optional per-bidder override) }
```

Seeding: JSON fixtures in `src/main/resources/seed/` (also used by tests), loaded via `scripts/seed.sh` (mongosh). Fixtures: 2 accounts, 3 publishers, 3 bidders (distinct targeting so filtering is demonstrable).

## 5. Config cache

- `PartnerConfigCache`: loads all three collections at startup (fail fast if Mongo is unreachable), `@Scheduled` full refresh every 30 s.
- Structures: `Map<accountKey, Account>`, `Map<accountId, Map<publisherId, Publisher>>`, `List<Bidder>` — replaced atomically per refresh (volatile snapshot object holding all three; readers never see a partial refresh).
- Refresh failure: log + keep last-good snapshot.
- Spring Data repositories are used by the cache loader only; nothing else touches Mongo at request time.

## 6. Request pipeline

Mini-layered: one small class per stage, wired by the controller. No pipeline framework.

```
BidController (byte[] in/out)
 → OrtbCodec            dsl-json parse; 400 on failure
 → SupplyResolver       accountKey → Account, body → Publisher; 403 on failure
 → RequestValidator     rules from §3; 400 on failure
 → BidderTargetingFilter status ACTIVE + format match + country match (device.geo.country, ISO-3166-1 alpha-3; a request with no country matches all bidders — the country filter applies only when the request carries one)
 → BidRequestForwarder  builds per-bidder outbound request (see §7)
 → FanOutService        parallel calls with a single auction deadline
 → WinnerSelector       first-price winner (see §8)
 → ResponseBuilder      single-seat BidResponse + adm macro substitution
 → async after response: WinNotifier (nurl), AuctionEventLogger (always)
```

Format derivation per request: union over imps (`banner` present → BANNER, `video` → VIDEO). A bidder is eligible if its `targeting.formats` intersects the request's formats.

## 7. Outbound requests, concurrency, tmax

- `effectiveTmax = clamp(request.tmax ?? 300, 100, 1000)` ms. Outbound `tmax = effectiveTmax − 50` (overhead reserve). Per-bidder `tmaxMs` override, if lower, wins for that bidder's request + timeout.
- Outbound body: the parsed request re-serialized with dsl-json, with `tmax` overwritten. **Fields not covered by the model are dropped** (dsl-json compiled mode has no unknown-field capture); declared `ext` maps survive. Accepted for the simulator — mirrors an exchange rebuilding requests.
- Fan-out: one `CompletableFuture.supplyAsync` per eligible bidder on a shared `Executors.newVirtualThreadPerTaskExecutor()` bean; gather with a single deadline of `effectiveTmax` (`orTimeout`/timed join). Late, failed, non-200, or unparseable bidders are dropped; each per-bidder outcome recorded as `BID | NO_BID(204) | TIMEOUT | ERROR` with latency for the event log + metrics.
- `RestClient`: connect timeout 100 ms, read timeout 1000 ms (hard cap) at the factory; the real per-auction bound is the future deadline.
- Failure isolation: one bidder's exception never affects others or the caller.

## 8. Auction: winner, macros, nurl

- Candidate bids: flatten all `seatbid[].bid[]`; keep bids where `impid` equals the **first imp's id** (v1 awards only the first imp — multi-imp is out of scope), `price ≥ imp.bidfloor` (default 0), `adm` non-empty.
- **First-price**: winner = max price (tie → first received); clearing price = bid price.
- Macros substituted in `adm` and `nurl`: `${AUCTION_ID}`, `${AUCTION_BID_ID}`, `${AUCTION_IMP_ID}`, `${AUCTION_SEAT_ID}`, `${AUCTION_PRICE}`.
- Response: `id` = request id, `cur` = "USD", one `seatbid` with the winning bidder's `seat`, one `bid`.
- `nurl`: fired server-side after the response is committed — fire-and-forget GET on a virtual thread, 2 s timeout, outcome only logged/metered. (Prod note: often client-side; here it exercises the loop for the dummy DSP later.)
- No loss notices, no `burl`.

## 9. Event log & observability

- **Auction event log**: exactly one structured JSON line per auction on logger `AUCTION_EVENTS` (logstash-logback-encoder): auctionId, accountKey, publisherId, requested formats, eligible bidder names, per-bidder `{name, outcome, latencyMs, price?}`, winner + clearing price, response status, total latency. Loki-ready; not stored in Mongo.
- **Metrics** (Micrometer → OTLP via `micrometer-registry-otlp`): counters `auctions_total`, `nobid_total{reason}`, `bidder_requests_total{bidder,outcome}`, `wins_total{bidder}`; timers `auction_duration`, `bidder_call_duration{bidder}`.
- **Traces**: `micrometer-tracing-bridge-otel` + OTLP span exporter; server span per auction, child span per bidder call.
- Exporters must degrade silently when no collector is reachable (local dev).

## 10. dsl-json specifics (for the executor)

- ORTB model classes: plain Java classes with **public mutable fields**, no Lombok on these (avoids Lombok↔dsl-json annotation-processor interplay), annotated `@CompiledJson`. Every object carries `public Map<String, Object> ext;`.
- Classes: `BidRequest, Imp, Banner, Video, Site, App, Publisher, Device, Geo, User, Source, Regs` / `BidResponse, SeatBid, Bid`. Only fields the simulator touches + `ext`.
- Singleton `DslJson<Object>` bean (`Settings.basicSetup()`-style config with service-loader so compiled converters register). Serialization must omit null fields (dsl-json omit-defaults/minimal object format policy — executor verifies exact enum for the chosen version).
- dsl-json annotation processor registered in `maven-compiler-plugin` `annotationProcessorPaths` alongside Lombok (Lombok first). Executor verifies whether the 2.x processor ships in the main artifact or needs `dsl-json-processor` for the pinned version.
- Controller signature works on `byte[]` (`@RequestBody byte[]`, returns `ResponseEntity<byte[]>`); no `HttpMessageConverter` registration for ORTB types, no Jackson involvement.
- Required unit test: round-trip a full sample 2.6 request fixture (deserialize → serialize → re-deserialize, assert stable).

## 11. Package layout

```
com.ming.sspexchange
├── api            BidController, GlobalExceptionHandler
├── config         virtual-thread executor bean, RestClient, DslJson bean, OTLP, @ConfigurationProperties
├── model
│   ├── openrtb    dsl-json classes (§10)
│   └── entity     AccountEntity, PublisherEntity, BidderEntity (Spring Data)
├── repository     AccountRepository, PublisherRepository, BidderRepository
├── cache          PartnerConfigCache (+ snapshot record, refresher)
└── service
    ├── supply     SupplyResolver, RequestValidator
    ├── demand     BidderTargetingFilter, BidRequestForwarder, FanOutService, BidderClient
    ├── auction    WinnerSelector, ResponseBuilder, MacroProcessor
    ├── event      AuctionEventLogger
    └── notify     WinNotifier
```

## 12. pom.xml changes

- Remove: `spring-boot-starter-mongodb`, `spring-boot-starter-mongodb-test` (driver-only starters, redundant next to `data-mongodb`).
- Keep: `webmvc`, `restclient`, `data-mongodb` + their test starters, Lombok (entities/services only).
- Add: `spring-boot-starter-actuator`, `com.dslplatform:dsl-json` (2.x, pinned), `micrometer-registry-otlp`, `micrometer-tracing-bridge-otel`, `io.opentelemetry:opentelemetry-exporter-otlp`, `net.logstash.logback:logstash-logback-encoder`; test: `spring-boot-testcontainers`, `org.testcontainers:mongodb` + `junit-jupiter`, WireMock (`org.wiremock`, artifact compatible with Boot 4 / Jetty 12).
- `application.properties` → `application.yaml`; `spring.threads.virtual.enabled=true`; Mongo URI + db `ssp-exchange`; OTLP endpoints via env-overridable properties.

## 13. Testing

- **Unit**: WinnerSelector (floor filter, tie, empty, wrong impid), BidderTargetingFilter (format/country/status matrix, missing geo), MacroProcessor, tmax clamping, SupplyResolver (403 cases), dsl-json round-trip (§10).
- **Integration** (`@SpringBootTest` + Testcontainers Mongo + WireMock): two stubbed bidders — one bids, one delays past tmax → assert 200 + winner correctness + nurl request received by stub; no-bidders-bid → 204; bad account → 403; malformed body → 400. Seed fixtures reused from `src/main/resources/seed/`.
- User runs all builds/tests: plan must hand over exact `./mvnw` commands.

## 14. Deployment

- **Docker**: multi-stage (JDK 25 build → JRE 25 runtime), non-root. Push to ECR `140023370575.dkr.ecr.eu-central-1.amazonaws.com/eks-dev/app`, tag `exchange-<version>`.
- **K8s, namespace `production`** (this service; ssp/dsp get their own namespaces later):
  - `Deployment` (1 replica, readiness/liveness on actuator health probes, resource requests/limits, OTLP endpoint env),
  - `Service` (ClusterIP),
  - `HTTPRoute` attached to the existing Gateway (gateway name/namespace taken from the infra repo — placeholder in manifests, user fills in),
  - MongoDB: single-replica `Deployment` + PVC + `Service mongodb` in `production` (simulator-grade; prod would be managed/Atlas),
  - seed `Job` (mongosh image + fixtures ConfigMap).
- **Local dev**: `docker-compose.yml` (mongo + auto-seed container); app via `./mvnw spring-boot:run`.
- User executes all deploy commands; plan provides them (docker build/push, kubectl apply order).

## 15. Out of scope (v1)

Second-price, deals/PMP, loss notices (`lurl`), `burl`/billing, IVT, privacy enforcement (TCF/GPP strings pass through `ext`/`regs` untouched), currency conversion (USD only), multi-imp awards (first imp only), admin CRUD API, Kafka/eventing, user matching/cookie sync.

## 16. Sequencing after v1

1. Dummy DSP service (own repo/namespace): configurable bid behavior (price ranges, no-bid rate, latency jitter) — becomes the target of `bidders.endpoint`.
2. Dummy supply SSP: traffic generator posting synthetic 2.6 requests at the exchange.
3. Wire end-to-end across namespaces; then LGTM dashboards off the OTLP/AUCTION_EVENTS data.
