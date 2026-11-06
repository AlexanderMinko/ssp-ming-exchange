package com.ming.sspexchange.service.auction;

import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.service.supply.RejectReason;

/** Every way a bid request can end. The controller maps each case to an HTTP response. */
public sealed interface RtbOutcome {

    RtbOutcome NO_BID = new NoBid();

    record Fill(BidResponse response) implements RtbOutcome { }

    record NoBid() implements RtbOutcome { }

    record Rejected(RejectReason reason) implements RtbOutcome { }
}
