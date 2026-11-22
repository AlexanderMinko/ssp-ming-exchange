package com.ming.sspexchange.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.ClientRequestObservationConvention;
import org.springframework.mock.http.client.MockClientHttpRequest;

import io.micrometer.common.KeyValue;

class BidderObservationConventionTest {

    private final ClientRequestObservationConvention convention = BidderClientConfig.bidderObservationConvention();

    @Test
    void spanIsNamedAfterTheBidder() {
        assertThat(convention.getContextualName(contextFor("alpha"))).isEqualTo("bid alpha");
    }

    @Test
    void bidderBecomesAKeyValue() {
        assertThat(convention.getLowCardinalityKeyValues(contextFor("alpha")))
                .contains(KeyValue.of(BidderClientConfig.BIDDER_ATTRIBUTE, "alpha"));
    }

    @Test
    void callWithoutTheAttributeIsNamedNone() {
        var context = new ClientRequestObservationContext(new MockClientHttpRequest(HttpMethod.POST, "/bid/alpha"));
        assertThat(convention.getContextualName(context)).isEqualTo("bid none");
    }

    @Test
    void lateResponseOutcomeIsTimeout() {
        var context = contextFor("gamma");
        context.setError(BidderClientConfig.LATE_RESPONSE);
        assertThat(convention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("outcome", "TIMEOUT"));
    }

    @Test
    void onTimeResponseKeepsSpringsOutcome() {
        assertThat(convention.getLowCardinalityKeyValues(contextFor("alpha")))
                .doesNotContain(KeyValue.of("outcome", "TIMEOUT"));
    }

    private static ClientRequestObservationContext contextFor(String bidderName) {
        var request = new MockClientHttpRequest(HttpMethod.POST, "/bid/{key}", bidderName);
        request.getAttributes().put(BidderClientConfig.BIDDER_ATTRIBUTE, bidderName);
        return new ClientRequestObservationContext(request);
    }
}
