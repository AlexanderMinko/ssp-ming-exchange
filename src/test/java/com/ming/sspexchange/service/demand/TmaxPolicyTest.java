package com.ming.sspexchange.service.demand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TmaxPolicyTest {

    @Test void defaultsWhenAbsent()   { assertThat(TmaxPolicy.effective(null)).isEqualTo(300); }
    @Test void clampsLow()            { assertThat(TmaxPolicy.effective(10)).isEqualTo(100); }
    @Test void clampsHigh()           { assertThat(TmaxPolicy.effective(5000)).isEqualTo(1000); }
    @Test void passesThroughInRange() { assertThat(TmaxPolicy.effective(350)).isEqualTo(350); }

    @Test void outboundSubtractsOverhead()      { assertThat(TmaxPolicy.outbound(350, null)).isEqualTo(300); }
    @Test void lowerBidderOverrideWins()        { assertThat(TmaxPolicy.outbound(350, 200)).isEqualTo(200); }
    @Test void higherBidderOverrideIsIgnored()  { assertThat(TmaxPolicy.outbound(350, 900)).isEqualTo(300); }
}
