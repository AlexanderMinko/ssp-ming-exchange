package com.ming.sspexchange.service.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.model.openrtb.SeatBid;
import com.ming.sspexchange.service.auction.WinningBid;

/**
 * (request, winner) → the exchange's single-seat {@link BidResponse}. The winning bid is copied field by
 * field with its adm/nurl macros substituted, so the bidder's original response object is never mutated.
 * nurl is substituted but NOT fired here: the supply-partner SSP triggers it on win (later phase).
 * Unmapped targets fail the build.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, imports = List.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BidResponseMapper {

    String CURRENCY = "USD";

    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "cur", constant = CURRENCY)
    @Mapping(target = "seatbid", expression = "java(List.of(toSeatBid(request, winner)))")
    @Mapping(target = "bidid", ignore = true)
    @Mapping(target = "nbr", ignore = true)
    @Mapping(target = "ext", ignore = true)
    BidResponse toBidResponse(BidRequest request, WinningBid winner);

    @Mapping(target = "seat", source = "winner.bidder.seat")
    @Mapping(target = "bid", expression = "java(List.of(toBid(request, winner)))")
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "ext", ignore = true)
    SeatBid toSeatBid(BidRequest request, WinningBid winner);

    @Mapping(target = ".", source = "winner.bid")
    @Mapping(target = "adm", expression = "java(substituteMacros(winner.bid().getAdm(), request, winner))")
    @Mapping(target = "nurl", expression = "java(substituteMacros(winner.bid().getNurl(), request, winner))")
    Bid toBid(BidRequest request, WinningBid winner);

    /** OpenRTB 2.6 §4.6 macros; a null template stays null, a null value substitutes as empty. */
    default String substituteMacros(String template, BidRequest request, WinningBid winner) {
        if (Objects.isNull(template)) {
            return null;
        }
        Bid bid = winner.bid();
        return template
                .replace("${AUCTION_ID}", nullSafe(request.getId()))
                .replace("${AUCTION_BID_ID}", nullSafe(bid.getId()))
                .replace("${AUCTION_IMP_ID}", nullSafe(bid.getImpid()))
                .replace("${AUCTION_SEAT_ID}", nullSafe(winner.bidder().getSeat()))
                .replace("${AUCTION_PRICE}", BigDecimal.valueOf(bid.getPrice()).toPlainString());
    }

    private static String nullSafe(String value) {
        return Objects.nonNull(value) ? value : "";
    }
}
