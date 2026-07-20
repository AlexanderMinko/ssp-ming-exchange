package com.ming.sspexchange.service.auction;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.SeatBid;
import com.ming.sspexchange.service.mapper.OrtbMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ResponseBuilder {

    private final MacroProcessor macroProcessor;
    private final OrtbMapper ortbMapper;

    // nurl is substituted but NOT fired here -- the supply-partner SSP triggers it on win (later phase).
    public BidResponse build(BidRequest request, WinningBid winner) {
        var ctx = new MacroContext(request.getId(), winner.bid().getId(), winner.bid().getImpid(),
                winner.bidder().getSeat(), winner.bid().getPrice());

        var bid = ortbMapper.copy(winner.bid());
        bid.setAdm(macroProcessor.substitute(bid.getAdm(), ctx));
        bid.setNurl(macroProcessor.substitute(bid.getNurl(), ctx));

        var seatBid = new SeatBid();
        seatBid.setSeat(winner.bidder().getSeat());
        seatBid.setBid(List.of(bid));

        var response = new BidResponse();
        response.setId(request.getId());
        response.setCur("USD");
        response.setSeatbid(List.of(seatBid));
        return response;
    }
}
