package com.ming.sspexchange.service.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ming.sspexchange.cache.SupplyPartnerCache;
import com.ming.sspexchange.model.RtbContext;
import com.ming.sspexchange.model.RtbData;
import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.model.entity.SupplyPartnerType;
import com.ming.sspexchange.model.openrtb.App;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Publisher;
import com.ming.sspexchange.model.openrtb.Site;

class SupplyResolverTest {

    private final SupplyPartnerCache cache = mock(SupplyPartnerCache.class);
    private final SupplyResolver resolver = new SupplyResolver(cache);

    private static final SupplyPartner SOURCE = SupplyPartner.builder()
            .id("s-1").entityType(SupplyPartnerType.SOURCE).sourceId("abc").build();
    private static final SupplyPartner PUBLISHER = SupplyPartner.builder()
            .id("p-1").entityType(SupplyPartnerType.PUBLISHER).publisherId("pub-100").build();

    private static BidRequest appRequest(String publisherId) {
        var pub = new Publisher(); pub.setId(publisherId);
        var app = new App(); app.setPublisher(pub);
        var request = new BidRequest(); request.setApp(app);
        return request;
    }

    @BeforeEach
    void defaults() {
        when(cache.source("abc")).thenReturn(Optional.of(SOURCE));
        when(cache.publisher(anyString())).thenReturn(Optional.empty());
        when(cache.publisher("pub-100")).thenReturn(Optional.of(PUBLISHER));
    }

    private static RtbContext context(String sourceId, BidRequest request) {
        var data = new RtbData();
        data.setSourceId(sourceId);
        return new RtbContext(request, data);
    }

    private RtbData resolved(String sourceId, BidRequest request) {
        var ctx = context(sourceId, request);
        assertThat(resolver.resolve(ctx)).isEmpty();
        return ctx.data();
    }

    private void assertRejected(String sourceId, BidRequest request, RejectReason reason) {
        assertThat(resolver.resolve(context(sourceId, request))).contains(reason);
    }

    @Test
    void registeredPublisherConfigApplies() {
        var data = resolved("abc", appRequest("pub-100"));

        assertThat(data.getSource()).isSameAs(SOURCE);
        assertThat(data.getPublisher()).isSameAs(PUBLISHER);
    }

    @Test
    void unknownPublisherFallsBackToSource() {
        var data = resolved("abc", appRequest("pub-999"));

        assertThat(data.getSource()).isSameAs(SOURCE);
        assertThat(data.getPublisher()).isNull();
    }

    @Test
    void missingPublisherIdFallsBackToSource() {
        var data = resolved("abc", new BidRequest());

        assertThat(data.getSource()).isSameAs(SOURCE);
        assertThat(data.getPublisher()).isNull();
    }

    @Test
    void missingSourceIsRejected() {
        assertRejected(null, appRequest("pub-100"), RejectReason.MISSING_SOURCE);
        assertRejected(" ", appRequest("pub-100"), RejectReason.MISSING_SOURCE);
    }

    @Test
    void sourceIdOfWrongLengthIsRejected() {
        assertRejected("abcd", appRequest("pub-100"), RejectReason.BAD_SOURCE_ID);
        assertRejected("ab", appRequest("pub-100"), RejectReason.BAD_SOURCE_ID);
    }

    @Test
    void unknownSourceIsRejected() {
        when(cache.source("zzz")).thenReturn(Optional.empty());
        assertRejected("zzz", appRequest("pub-100"), RejectReason.UNKNOWN_SOURCE);
    }

    @Test
    void siteRequestsResolveViaSitePublisher() {
        var pub = new Publisher(); pub.setId("pub-100");
        var site = new Site(); site.setPublisher(pub);
        var request = new BidRequest(); request.setSite(site);

        assertThat(resolved("abc", request).getPublisher()).isSameAs(PUBLISHER);
    }
}
