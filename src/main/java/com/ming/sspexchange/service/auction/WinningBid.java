package com.ming.sspexchange.service.auction;

import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.openrtb.Bid;

public record WinningBid(BidderEntity bidder, Bid bid) { }
