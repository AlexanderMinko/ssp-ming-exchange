package com.ming.sspexchange.service.auction;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class MacroProcessor {

    public String substitute(String template, MacroContext ctx) {
        if (Objects.isNull(template)) return null;
        return template
                .replace("${AUCTION_ID}", nullSafe(ctx.auctionId()))
                .replace("${AUCTION_BID_ID}", nullSafe(ctx.bidId()))
                .replace("${AUCTION_IMP_ID}", nullSafe(ctx.impId()))
                .replace("${AUCTION_SEAT_ID}", nullSafe(ctx.seat()))
                .replace("${AUCTION_PRICE}", BigDecimal.valueOf(ctx.price()).toPlainString());
    }

    private static String nullSafe(String s) {
        return Objects.nonNull(s) ? s : "";
    }
}
