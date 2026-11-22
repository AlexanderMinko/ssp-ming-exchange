package com.ming.sspexchange.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;

class TracingConfigTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PARENT_SPAN_ID = "00f067aa0ba902b7";

    private final TracingConfig config = new TracingConfig();
    private final Sampler sampler = config.serverRootSampler(1.0);
    private final ObservationPredicate predicate = config.skipActuatorRequests();

    @Test
    void serverSpanWithoutParentIsSampled() {
        assertThat(decide(Context.root(), SpanKind.SERVER)).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
    }

    @Test
    void internalSpanWithoutParentIsDropped() {
        assertThat(decide(Context.root(), SpanKind.INTERNAL)).isEqualTo(SamplingDecision.DROP);
    }

    @Test
    void spanUnderSampledRemoteParentFollowsIt() {
        var parentContext = Context.root().with(Span.wrap(SpanContext.createFromRemoteParent(
                TRACE_ID, PARENT_SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault())));
        assertThat(decide(parentContext, SpanKind.INTERNAL)).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
    }

    @Test
    void actuatorRequestsAreNotObserved() {
        assertThat(predicate.test("http.server.requests", requestTo("/actuator/prometheus"))).isFalse();
    }

    @Test
    void auctionRequestsAreObserved() {
        assertThat(predicate.test("http.server.requests", requestTo("/rtb/bid"))).isTrue();
    }

    private SamplingDecision decide(Context parentContext, SpanKind spanKind) {
        return sampler.shouldSample(parentContext, TRACE_ID, "span", spanKind, Attributes.empty(), List.of())
                .getDecision();
    }

    private static ServerRequestObservationContext requestTo(String path) {
        return new ServerRequestObservationContext(new MockHttpServletRequest("POST", path),
                new MockHttpServletResponse());
    }
}
