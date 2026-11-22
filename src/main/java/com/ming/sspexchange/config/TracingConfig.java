package com.ming.sspexchange.config;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

/**
 * Which spans become traces. Every request is traced (no sampling, decided 21/11/2026); the two beans only keep
 * noise out: background roots and actuator calls. Same rules as Start.io's ssp-backend, minus its sampling.
 */
@Configuration
public class TracingConfig {

    private static final String ACTUATOR_PATH = "/actuator";

    /**
     * Replaces Boot's default sampler (parent-based ratio). A span with a parent follows the parent's decision: the
     * bidder client spans join the auction's server span, and the traceparent they send makes the bidders join too. A
     * span without a parent becomes a trace only when it is a SERVER span (an incoming request): a @Scheduled tick such
     * as the 30 s bidder and supply-partner cache refresh is dropped, together with everything under it. The tick's metrics are kept, because only the span is
     * dropped, not the observation. The probability stays a knob (TRACING_SAMPLING_PROBABILITY, default 1.0) for load
     * tests only.
     */
    @Bean
    Sampler serverRootSampler(@Value("${management.tracing.sampling.probability}") double probability) {
        return Sampler.parentBased(new ServerRootSampler(Sampler.traceIdRatioBased(probability)));
    }

    /**
     * No observation for /actuator/** (probes, the vmagent scrape every 30 s): each would otherwise be its own trace.
     * This switches the whole observation off, so actuator calls also leave http_server_requests_seconds.
     */
    @Bean
    ObservationPredicate skipActuatorRequests() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext request
                && StringUtils.startsWith(request.getCarrier().getRequestURI(), ACTUATOR_PATH));
    }

    /** The root decision: SERVER spans go to the ratio sampler, every other kind is dropped. */
    record ServerRootSampler(Sampler ratioSampler) implements Sampler {

        @Override
        public SamplingResult shouldSample(Context parentContext, String traceId, String name, SpanKind spanKind,
                Attributes attributes, List<LinkData> parentLinks) {
            if (spanKind != SpanKind.SERVER) {
                return SamplingResult.drop();
            }
            return ratioSampler.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
        }

        @Override
        public String getDescription() {
            return "ServerRootSampler{" + ratioSampler.getDescription() + "}";
        }
    }
}
