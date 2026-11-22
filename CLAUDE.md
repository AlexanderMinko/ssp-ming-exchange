# Project Context — SSP/Exchange Simulator (read this first)

Briefs you (the terminal AI) on who I am, what I'm building, and how to work with me. **This is the inverse of my EKS-infra project: here AdTech and reactive Java are my strengths, not my blind spot.** Adjust accordingly.

## Who I am
- Senior Java backend engineer (~5 yrs) working on a real AdTech SSP/Exchange: Spring WebFlux/Netty, Reactor, Kafka, MongoDB, Kubernetes at large scale.
- **This is my domain.** SSP/DSP/Exchange mechanics, OpenRTB, the auction/bid lifecycle, and reactive Java are things I know cold.
- Treat me as a peer and sounding board, **not** a student. No tutorials on OpenRTB, the bid flow, Spring, Reactor, or Java. Skip the 101 and get to the point.

## What I'm building
- A **minimal SSP/Exchange simulator**, deployed on the EKS cluster I built by hand (separate infra repo).
- **Core:** one HTTP endpoint that receives an **OpenRTB** bid request and returns a response sourced from a DSP. Minimal exchange workflow — only what's needed to demonstrate the path end-to-end.
- Plus two convenience components for end-to-end testing:
  - a **supply-partner SSP** (traffic source), and
  - a **demand-partner DSP** (bidder) — repo `…/AWS_EKS/ssp-ming-bidders` (the exchange's in-process `mock:` bidders were removed 07/11/2026; bidder endpoints are paths joined to `BIDDERS_BASE_URL`).
- **Namespace: everything deploys into `production` for now** (pre-created by Terraform `platform/`, `var.app_namespaces`). Namespace-per-component (`ssp`/`exchange`/`dsp`) is a possible later split — don't assume it until decided.
- **Per-component specifics (endpoints, payloads, exact workflow) I describe in the relevant channel.** Don't assume beyond what I've stated; ask if a detail is load-bearing and unspecified.

## Deployment target (the infra I already built)
- EKS cluster **`eks-dev`**, region **`eu-central-1`** (separate Terraform repo at `…/AWS_EKS/ssp-ming-exchange-terraform`; GitOps config repo at `…/AWS_EKS/ssp-ming-exchange-helm`).
- Images → ECR: `140023370575.dkr.ecr.eu-central-1.amazonaws.com/eks-dev/exchange` (eu-central-1).
- Edge via the **Kubernetes Gateway API** (AWS LB Controller → ALB), an `HTTPRoute` per service.
- That cluster will also run SigNoz + VictoriaMetrics observability and an Istio ambient mesh later — so build the services to be **trace/mesh-friendly** (emit OTLP where it's cheap to).
- **Observability stack = Start.io's** (decided 16/11/2026, replaces LGTM): **VictoriaMetrics** for metrics (`victoria-metrics-k8s-stack`: VM operator, `vmagent`, `vmsingle`, Grafana), **SigNoz on ClickHouse** for logs and traces (SigNoz `otel-agent` DaemonSet tails `/var/log/pods`; apps send traces over OTLP to the SigNoz collector). Nodes = 3 × `t3.large`.
- **App metrics = PULL** (decided 13/11/2026; backend VictoriaMetrics since 16/11/2026): apps expose `/actuator/prometheus` (`micrometer-registry-prometheus`); `vmagent` scrapes them via a `ServiceMonitor` in each app chart (the VM operator converts it to a `VMServiceScrape`; locally: a scrape job in `ssp-ming-exchange/observability/prometheus.yml`, read by the Compose VictoriaMetrics single). OTLP metrics push stays in the code but off. Why: `up` distinguishes dead from idle, `pod`/`namespace` labels come free, matches the KEDA query (Phase 15, PromQL against VictoriaMetrics).

## How to work with me
1. **Assume domain expertise.** No AdTech/OpenRTB/Spring/Reactor explainers. Lead with the answer.
2. **Be concise.** Density over volume — tight bullets/tables, no filler, no recap paragraphs.
3. **Peer-review and push back.** If a design is wrong, latency-naive, or not how a real exchange behaves, say so plainly. Honest peer beats yes-man.
4. **Simulator-grade, not prod.** Simplest thing that demonstrates the workflow. Flag "prod would do X; here Y is fine."
5. **Code ownership:** generate boilerplate/scaffolding freely (Spring skeleton, Dockerfile, k8s manifests); for domain/core logic, propose and let me decide. **I run builds/tests/deploys myself** — hand me the commands.

## Conventions / stack (confirm per component; some TBD)
- Java + Spring Boot 4, **Spring MVC on virtual threads** (`spring-boot-starter-webmvc`, `spring.threads.virtual.enabled`) — not WebFlux. Context (trace, MDC) is ThreadLocal: any executor that runs request work must restore it (`bidderExecutor` is wrapped with `ContextExecutorService`).
- OpenRTB version: TBD (I'll specify).
- Containerized (Docker) → ECR → EKS via Gateway API `HTTPRoute` (attached to the shared `edge/shared` Gateway), all in namespace `production`.
- **Code style (enforce on every change):**
  - **Apache Commons Lang 3** for null-safe checks: `StringUtils.isBlank/isNotBlank/isEmpty`, `ObjectUtils.isEmpty` for collections/maps. Not `s == null || s.isBlank()`.
  - **Prefer the Stream API over plain `for` loops.** Keep a loop only where a stream is clearly worse (checked exceptions, early-exit with side effects) — say why in a comment.
  - **No exceptions on the hot path** (the per-request bid flow). Expected outcomes — rejects, no-bid, bidder timeout/error — are return values, not throws (`RtbOrchestrator` returns `Optional<BidResponse>`; empty = 204). Exceptions only for genuinely exceptional failures (startup, config load, programming errors).
  - **No single-character identifiers** — variables, parameters, lambda params, loop counters included
    (`var r`, `e ->`, `for (int i …)` are out). Use a descriptive name: `response`, `bidder`, `index`. Exception: OpenRTB model fields keep their wire names (`w`, `h`).
  - **Always use curly braces** on `if` / `else` / `for` / `while` bodies, even one-liners.
- **Local vs cluster config** (decided 15/11/2026): one image, config from env. **Locations** (hosts, URLs, Mongo URI) have **no default** in `application.yaml` — the cluster value comes from the chart's ConfigMap/Secret, the laptop value from `application-local.yaml` (`SPRING_PROFILES_ACTIVE=local`). A key missing from the chart then fails startup instead of silently calling `localhost`. Tuning knobs (timeouts, toggles) keep defaults. Tests fill the gaps in `src/test/resources/config/application.yaml`.
- **Logs = one JSON object per stdout line** (decided 16/11/2026): `logback-spring.xml` sends Spring's own lines through Boot's `StructuredLogEncoder` (logstash format) and the `AUCTION_EVENTS` lines as bare JSON (shape unchanged). Profile `local` keeps text for Spring's lines. The SigNoz agent parses every line; stack traces stay inside one line.
- **Local logs + traces = SigNoz in Docker Compose** (decided 17/11/2026): `ssp-ming-exchange/observability/signoz/` (copied from SigNoz Foundry v0.5.0, images pinned to the EKS versions, UI on `:3301`, OTLP on `:4317`/`:4318`), pulled into the exchange's `docker-compose.yml` with `include:`. In the `local` profile only, the apps PUSH logs over OTLP (`opentelemetry-logback-appender-1.0` 2.28.1-alpha, installed by an `otlpLogAppenderInstaller` bean, endpoint in `application-local.yaml`). On EKS the SigNoz agent tails stdout instead.
- **Tracing config uses Boot 4 names** (fixed 17/11/2026): `spring-boot-starter-opentelemetry` (as in bidders), `management.opentelemetry.tracing.export.otlp.endpoint` and `management.tracing.export.enabled`. The old `management.tracing.enabled` / `management.otlp.tracing.endpoint` are deprecated at level `error` and no longer bind.
- **Traces = every request, no sampling** (decided 21/11/2026; ssp-backend's design minus its sampling): apps push OTLP/HTTP straight to `signoz-otel-collector.signoz:4318` (`OTLP_ENDPOINT`, no default in `application.yaml`; local value in `application-local.yaml`). `TracingConfig` in each app: a `Sampler` that keeps parent decisions and starts a trace only from a SERVER span (drops `@Scheduled` roots), and an `ObservationPredicate` that skips `/actuator/**`. Exchange → bidders `traceparent` is kept on purpose (unlike ssp-backend, our DSPs are ours). `TRACING_SAMPLING_PROBABILITY` stays 1.0; lower it only for a Gatling run (ClickHouse PVC 20Gi). The `k8s-infra` agent parses the JSON lines and lifts MDC `traceId`/`spanId` into the log record (`trace_parser`), so logs link to traces. `TRACING_ENABLED` is still `false` in the charts until SigNoz runs. `management.tracing.export.enabled` stays `true` always: Boot builds the W3C propagator only under it, so with it `false` no `traceparent` is read or sent and each service mints its own trace id (found 21/11/2026). `TRACING_ENABLED` (default `true`) maps to `management.tracing.export.otlp.enabled` and only switches sending spans.
- **Grafana dashboards = git** (decided 22/11/2026): repo `ssp-ming-monitoring` holds `dashboards/<folder>/<uid>.json`, the only source (neither Grafana persists anything). Local Compose Grafana bind-mounts it (file provider, UI edits allowed; `scripts/pull.sh <uid>` writes them back, or they die with the container). EKS: Argo CD app `grafana-dashboards` (helm repo `argocd/apps/`) renders the repo's chart into one ConfigMap per dashboard in `monitoring` (label `grafana_dashboard=1`, annotation `grafana_folder`), loaded read-only by the VM stack's Grafana sidecar (`folderAnnotation` + `foldersFromFilesStructure` set in `platform/06-monitoring.tf`). Datasource uid `VictoriaMetrics` in both Grafanas (the chart's uid; the local one changed from `victoriametrics`). `scripts/check.sh` lints.
- **Bidder client spans** (21/11/2026): named `bid <bidder>` with a `bidder` key (`BidderClientConfig#bidderObservationConvention`, the name passed as a RestClient attribute). Each call has one verdict future, settled atomically by whichever comes first: the reply (`verdict.complete` inside `exchange()`, while the client span is open) or `completeOnTimeout`. When the timeout won, the reply gets the stackless marker error `LATE_RESPONSE` on its client observation (`BidderService#settle`), so the span is an error with `outcome=TIMEOUT` and SigNoz equals `exchange_bidder_requests_total` exactly (22/11/2026; a clock check was off by ~0.1%).
- **Late bidder calls are never cancelled** (22/11/2026, as in ssp-backend): `completeOnTimeout` fixes the auction verdict, the HTTP call runs on and keeps its connection. The client read timeout (400 ms) only ends hung calls and must stay >= the largest bidder window: `MAX_TMAX_MS` 450 - `OVERHEAD_MS` 50 = 400 ms. Raise both together.
- **Synthetic data only.** All bid requests/responses are simulated — no real customer PII, no production revenue data. (Matches org data-handling rules.)

## Sequencing
- Start with the Exchange's single endpoint (OpenRTB in → DSP response out), then the dummy DSP, then the supply SSP; wire them end-to-end (in-cluster Service DNS within `production`).
- Deploy early and often to the real cluster — the point is the whole system (app + platform) running together.
