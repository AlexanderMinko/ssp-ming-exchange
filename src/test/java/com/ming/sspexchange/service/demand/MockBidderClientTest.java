package com.ming.sspexchange.service.demand;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.entity.MockConfig;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;

class MockBidderClientTest {

    private final MockBidderClient client = new MockBidderClient();

    private static OutboundRequest out(MockConfig mock) {
        var bidder = BidderEntity.builder().id("m").name("mock-alpha").seat("seat-mock")
                .endpoint("mock:alpha").status(PartnerStatus.ACTIVE).mock(mock).build();
        var imp = new Imp(); imp.setId("imp-1");
        var request = new BidRequest(); request.setId("req-1"); request.setImp(List.of(imp));
        return new OutboundRequest(bidder, request, 300);
    }

    @Test
    void fabricatesSingleBidWithMacroPlaceholders() {
        var result = client.call(out(MockConfig.builder().price(3.0).build()));

        assertThat(result.outcome()).isEqualTo(BidderOutcome.BID);
        var bid = result.response().getSeatbid().get(0).getBid().get(0);
        assertThat(bid.getImpid()).isEqualTo("imp-1");
        assertThat(bid.getPrice()).isEqualTo(3.0);
        assertThat(bid.getAdm()).contains("${AUCTION_ID}").contains("${AUCTION_PRICE}");
        assertThat(bid.getNurl()).contains("${AUCTION_PRICE}");
        assertThat(result.response().getSeatbid().get(0).getSeat()).isEqualTo("seat-mock");
    }

    @Test
    void missingMockConfigIsError() {
        assertThat(client.call(out(null)).outcome()).isEqualTo(BidderOutcome.ERROR);
    }

    @Test
    void zeroBidRateNeverBids() {
        var result = client.call(out(MockConfig.builder().price(3.0).bidRate(0.0).build()));
        assertThat(result.outcome()).isEqualTo(BidderOutcome.NO_BID);
    }

    @Test
    void latencyIsSimulated() {
        long start = System.nanoTime();
        client.call(out(MockConfig.builder().price(3.0).latencyMs(100).build()));
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(100);
    }
}
