package com.ming.sspexchange.service.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ming.sspexchange.cache.PartnerConfigCache;
import com.ming.sspexchange.model.entity.SupplyPartnerType;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.model.openrtb.App;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Publisher;
import com.ming.sspexchange.model.openrtb.Site;
import com.ming.sspexchange.model.RtbContext;
import com.ming.sspexchange.model.RtbData;

class SupplyResolverTest {

    private final PartnerConfigCache cache = mock(PartnerConfigCache.class);
    private final SupplyResolver resolver = new SupplyResolver(cache);

    private static final SupplyPartner SOURCE = SupplyPartner.builder()
            .id("s-1").entityType(SupplyPartnerType.SOURCE).sourceId("abc")
            .status(PartnerStatus.ACTIVE).build();
    private static final SupplyPartner PUBLISHER = SupplyPartner.builder()
            .id("p-1").entityType(SupplyPartnerType.PUBLISHER).publisherId("pub-100")
            .status(PartnerStatus.ACTIVE).build();

    private static BidRequest appRequest(String publisherId) {
        var pub = new Publisher(); pub.setId(publisherId);
        var app = new App(); app.setPublisher(pub);
        var r = new BidRequest(); r.setApp(app);
        return r;
    }

    @BeforeEach
    void defaults() {
        when(cache.source("abc")).thenReturn(Optional.of(SOURCE));
        when(cache.publisher(anyString())).thenReturn(Optional.empty());
        when(cache.publisher("pub-100")).thenReturn(Optional.of(PUBLISHER));
    }

    private RtbData resolved(String sourceId, BidRequest r) {
        var ctx = RtbContext.start(r, sourceId);
        assertThat(resolver.resolve(ctx)).isEmpty();
        return ctx.data();
    }

    private void assertRejected(String sourceId, BidRequest r, RejectReason reason) {
        assertThat(resolver.resolve(RtbContext.start(r, sourceId))).contains(reason);
    }

    @Test
    void registeredPublisherConfigApplies() {
        var data = resolved("abc", appRequest("pub-100"));

        assertThat(data.getSource()).isSameAs(SOURCE);
        assertThat(data.getPublisher()).isSameAs(PUBLISHER);
        assertThat(data.getSupplier()).isSameAs(PUBLISHER);
        assertThat(data.getPublisherId()).isEqualTo("pub-100");
    }

    @Test
    void unknownPublisherFallsBackToSource() {
        var data = resolved("abc", appRequest("pub-999"));

        assertThat(data.getSupplier()).isSameAs(SOURCE);
        assertThat(data.getPublisher()).isNull();
    }

    @Test
    void missingPublisherIdFallsBackToSource() {
        assertThat(resolved("abc", new BidRequest()).getSupplier()).isSameAs(SOURCE);
    }

    @Test
    void pausedPublisherIsRejected() {
        var paused = SupplyPartner.builder().id("p-2").entityType(SupplyPartnerType.PUBLISHER)
                .publisherId("pub-200").status(PartnerStatus.PAUSED).build();
        when(cache.publisher("pub-200")).thenReturn(Optional.of(paused));

        assertRejected("abc", appRequest("pub-200"), RejectReason.PAUSED_PUBLISHER);
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
    void pausedSourceIsRejected() {
        var paused = SupplyPartner.builder().id("s-2").entityType(SupplyPartnerType.SOURCE)
                .sourceId("old").status(PartnerStatus.PAUSED).build();
        when(cache.source("old")).thenReturn(Optional.of(paused));
        assertRejected("old", appRequest("pub-100"), RejectReason.UNKNOWN_SOURCE);
    }

    @Test
    void siteRequestsResolveViaSitePublisher() {
        var pub = new Publisher(); pub.setId("pub-100");
        var site = new Site(); site.setPublisher(pub);
        var r = new BidRequest(); r.setSite(site);

        assertThat(resolved("abc", r).getPublisherId()).isEqualTo("pub-100");
    }
}
