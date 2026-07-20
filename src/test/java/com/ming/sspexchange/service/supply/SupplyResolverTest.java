package com.ming.sspexchange.service.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ming.sspexchange.cache.PartnerConfigCache;
import com.ming.sspexchange.model.entity.AccountEntity;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.entity.PublisherEntity;
import com.ming.sspexchange.model.openrtb.App;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Publisher;
import com.ming.sspexchange.model.openrtb.Site;

class SupplyResolverTest {

    private final PartnerConfigCache cache = mock(PartnerConfigCache.class);
    private final SupplyResolver resolver = new SupplyResolver(cache);

    private static final AccountEntity ACCOUNT = AccountEntity.builder()
            .id("acc-1").accountKey("key-1").status(PartnerStatus.ACTIVE).build();
    private static final PublisherEntity PUBLISHER = PublisherEntity.builder()
            .id("p-1").accountId("acc-1").publisherId("pub-100").status(PartnerStatus.ACTIVE).build();

    private static BidRequest appRequest(String publisherId) {
        var pub = new Publisher(); pub.setId(publisherId);
        var app = new App(); app.setPublisher(pub);
        var r = new BidRequest(); r.setApp(app);
        return r;
    }

    @Test
    void resolvesActiveAccountAndPublisher() {
        when(cache.accountByKey("key-1")).thenReturn(Optional.of(ACCOUNT));
        when(cache.publisher("acc-1", "pub-100")).thenReturn(Optional.of(PUBLISHER));

        var ctx = resolver.resolve("key-1", appRequest("pub-100"));

        assertThat(ctx.account().getId()).isEqualTo("acc-1");
        assertThat(ctx.publisher().getPublisherId()).isEqualTo("pub-100");
    }

    @Test
    void unknownAccountIsAuthFailure() {
        when(cache.accountByKey("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve("nope", appRequest("pub-100")))
                .isInstanceOf(SupplyAuthException.class);
    }

    @Test
    void pausedAccountIsAuthFailure() {
        var paused = AccountEntity.builder().id("acc-1").status(PartnerStatus.PAUSED).build();
        when(cache.accountByKey("key-1")).thenReturn(Optional.of(paused));
        assertThatThrownBy(() -> resolver.resolve("key-1", appRequest("pub-100")))
                .isInstanceOf(SupplyAuthException.class);
    }

    @Test
    void unknownOrPausedPublisherIsAuthFailure() {
        when(cache.accountByKey("key-1")).thenReturn(Optional.of(ACCOUNT));
        when(cache.publisher("acc-1", "pub-999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve("key-1", appRequest("pub-999")))
                .isInstanceOf(SupplyAuthException.class);
    }

    @Test
    void missingPublisherIdIsValidationFailure() {
        when(cache.accountByKey("key-1")).thenReturn(Optional.of(ACCOUNT));
        assertThatThrownBy(() -> resolver.resolve("key-1", new BidRequest()))
                .isInstanceOf(InvalidBidRequestException.class);
    }

    @Test
    void siteRequestsResolveViaSitePublisher() {
        when(cache.accountByKey("key-1")).thenReturn(Optional.of(ACCOUNT));
        when(cache.publisher("acc-1", "pub-100")).thenReturn(Optional.of(PUBLISHER));
        var pub = new Publisher(); pub.setId("pub-100");
        var site = new Site(); site.setPublisher(pub);
        var r = new BidRequest(); r.setSite(site);

        assertThat(resolver.resolve("key-1", r).publisher().getPublisherId()).isEqualTo("pub-100");
    }
}
