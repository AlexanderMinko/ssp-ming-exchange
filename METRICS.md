# Metrics — exchange

The exchange exposes its metrics at `GET /actuator/prometheus` (`micrometer-registry-prometheus`).
VictoriaMetrics scrapes them under `job="exchange"`: `vmagent` in the cluster, the Compose VictoriaMetrics single
locally (`observability/prometheus.yml`). Verified on 17/11/2026 against VictoriaMetrics, last 7 days of data.

## Custom metrics

`ExchangeMetrics` (`service/metrics/ExchangeMetrics.java`) records all of them. The table lists the Prometheus names.

| Metric | Type | Labels | Meaning |
|---|---|---|---|
| `exchange_auctions_total` | counter | none | Auctions that ran. Rejects are not counted. |
| `exchange_auction_duration_seconds` | timer: `_count`, `_sum`, `_max` | none | End-to-end auction time, request in to response out |
| `exchange_bidder_requests_total` | counter | `bidder`, `outcome` | Bidder calls by result |
| `exchange_bidder_call_duration_seconds` | timer: `_count`, `_sum`, `_max` | `bidder` | Time of one bidder call; a TIMEOUT is recorded at its deadline |
| `exchange_wins_total` | counter | `bidder` | Auctions each bidder won |
| `exchange_nobid_total` | counter | `reason` | Auctions that ended without a winner |
| `exchange_reject_total` | counter | `reason` | Requests rejected before an auction (answered 204, or 400 for malformed JSON) |

### Label values

These are all the values the code can emit.

| Label | Metric | Values |
|---|---|---|
| `bidder` | `exchange_bidder_*`, `exchange_wins_total` | bidder `name` from the `bidders` collection: `alpha`, `beta`, `gamma` |
| `outcome` | `exchange_bidder_requests_total` | `BID`, `NO_BID`, `TIMEOUT`, `ERROR` |
| `reason` | `exchange_nobid_total` | `no_bids`, `no_valid_bids`, `no_bidders_configured` |
| `reason` | `exchange_reject_total` | every `RejectReason` value, plus `MALFORMED_JSON` |

The `RejectReason` values are `MISSING_SOURCE`, `BAD_SOURCE_ID`, `UNKNOWN_SOURCE`, `MISSING_REQUEST_ID`, `MISSING_IMP`,
`APP_XOR_SITE`, `IMP_MISSING_ID`, `IMP_NO_FORMAT` and `NEGATIVE_BIDFLOOR`.

### Example queries

Rates are per second. `$__rate_interval` is Grafana's; outside Grafana use a window such as `[1m]`.

```promql
# Auctions per second
rate(exchange_auctions_total[$__rate_interval])

# Fill rate, from 0 to 1
sum(rate(exchange_wins_total[$__rate_interval])) / sum(rate(exchange_auctions_total[$__rate_interval]))

# Average auction time, in seconds
rate(exchange_auction_duration_seconds_sum[$__rate_interval])
  / rate(exchange_auction_duration_seconds_count[$__rate_interval])

# Slowest recent auction, in seconds
exchange_auction_duration_seconds_max

# Bidder calls per second by bidder and outcome
sum by (bidder, outcome) (rate(exchange_bidder_requests_total[$__rate_interval]))

# Timeout share per bidder, from 0 to 1
sum by (bidder) (rate(exchange_bidder_requests_total{outcome="TIMEOUT"}[$__rate_interval]))
  / sum by (bidder) (rate(exchange_bidder_requests_total[$__rate_interval]))

# Average bidder call time per bidder, in seconds
sum by (bidder) (rate(exchange_bidder_call_duration_seconds_sum[$__rate_interval]))
  / sum by (bidder) (rate(exchange_bidder_call_duration_seconds_count[$__rate_interval]))

# Slowest recent bidder call per bidder, in seconds
max by (bidder) (exchange_bidder_call_duration_seconds_max)

# Share of wins per bidder, from 0 to 1
sum by (bidder) (rate(exchange_wins_total[$__rate_interval]))
  / ignoring(bidder) group_left sum(rate(exchange_wins_total[$__rate_interval]))

# No-bids and rejects per second by reason
sum by (reason) (rate(exchange_nobid_total[$__rate_interval]))
sum by (reason) (rate(exchange_reject_total[$__rate_interval]))
```

## Built-in metrics

Spring Boot and Micrometer export these without any code in the exchange.

| Group | Metrics | What they show |
|---|---|---|
| HTTP server | `http_server_requests_seconds_*`, `http_server_requests_active_seconds_*` | Requests served, by `uri`, `method`, `status`, `outcome`; `/rtb/bid` is the bid endpoint |
| HTTP client | `http_client_requests_seconds_*`, `http_client_requests_active_seconds_*` | Calls to the bidders service, by `uri` (`/bid/alpha` …), `status`, `outcome`, `client_name` |
| MongoDB driver | `mongodb_driver_commands_seconds_*`, `mongodb_driver_pool_*` | Mongo command times; connection pool size, checked-out, wait queue, checkout failures |
| Spring Data | `spring_data_repository_invocations_seconds_*` | Repository calls (config cache refreshes, seeding) |
| Scheduled tasks | `tasks_scheduled_execution_seconds_*`, `tasks_scheduled_execution_active_seconds_*` | `@Scheduled` runs, such as the 30 s config cache refresh |
| JVM memory | `jvm_memory_used_bytes`, `jvm_memory_committed_bytes`, `jvm_memory_max_bytes`, `jvm_memory_usage_after_gc`, `jvm_buffer_*` | Heap and non-heap by `area` and `id`; direct and mapped buffers |
| JVM GC | `jvm_gc_pause_seconds_*`, `jvm_gc_concurrent_phase_time_seconds_*`, `jvm_gc_cpu_time_ns_total`, `jvm_gc_overhead`, `jvm_gc_memory_allocated_bytes_total`, `jvm_gc_memory_promoted_bytes_total`, `jvm_gc_live_data_size_bytes`, `jvm_gc_max_data_size_bytes` | Pauses by `cause` and `action`, allocation and promotion rates, GC CPU share |
| JVM threads and classes | `jvm_threads_*`, `jvm_classes_*`, `jvm_compilation_time_ms_total`, `jvm_info` | Platform threads by `state` (virtual threads are not counted), loaded classes, JIT time, JVM version |
| Process and system | `process_*`, `system_cpu_usage`, `system_cpu_count`, `system_load_average_1m`, `disk_free_bytes`, `disk_total_bytes` | CPU, open files, uptime, host load, disk |
| Tomcat | `tomcat_sessions_*` | HTTP sessions (unused by the exchange; always near 0) |
| Logging | `logback_events_total` | Log lines by `level`; a rise in `level="warn"` or `"error"` is worth a look |
| Startup | `application_started_time_seconds`, `application_ready_time_seconds` | Time to start and to become ready |

VictoriaMetrics adds `up` and the `scrape_*` series itself for each scrape. `up{job="exchange"} == 0` means it is down.

## Notes

- **Percentiles: HTTP server only** (since 24/11/2026). `http_server_requests_seconds_bucket` exists, so
  `histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/rtb/bid"}[1m])))` gives p99.
  `HTTP_SERVER_HISTOGRAM_ENABLED=false` turns the buckets off. No other timer exports `_bucket` series. For those, use
  `_sum / _count` for the average and `_max` for the worst case, or enable
  `management.metrics.distribution.percentiles-histogram.<metric name>` in `application.yaml`.
- **Bucket boundaries.** Micrometer's generated ladder plus exact `slo` boundaries at 25, 50, 100, 200, **250, 300,
  350** and 500 ms (`management.metrics.distribution.slo`). Between ladder boundaries `histogram_quantile` assumes an
  even spread, so a percentile is only as precise as its bucket. Check: a p99 above `_max` means the bucket is too wide,
  not that requests were slow. The 250/300/350 ms boundaries are the auction deadlines, so the share of requests over a
  deadline is exact: `1 - sum(rate(..._bucket{le="0.3"}[1m])) / sum(rate(..._bucket{le="+Inf"}[1m]))`.
- **`_max` is recent, not all-time.** Micrometer decays it over about 2 minutes.
- **Two views of a bidder timeout.** `exchange_bidder_requests_total{outcome="TIMEOUT"}` counts the deadline the
  exchange enforces. `http_client_requests_seconds_count{status="CLIENT_ERROR"}` counts calls that ended in an I/O
  error or read timeout inside the HTTP client.
- **Bidders side.** The bidders service's own metrics are listed in `ssp-ming-bidders/METRICS.md`.
