package com.ming.sspexchange.service.auction;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.openrtb.Bid;

public record WinningBid(Bidder bidder, Bid bid) { }
