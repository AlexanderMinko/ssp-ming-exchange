package com.ming.sspexchange.service.auction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MacroProcessorTest {

    private final MacroProcessor macros = new MacroProcessor();
    private final MacroContext ctx = new MacroContext("req-1", "bid-9", "imp-1", "seat-a", 2.5);

    @Test
    void substitutesAllMacros() {
        var in = "${AUCTION_ID}|${AUCTION_BID_ID}|${AUCTION_IMP_ID}|${AUCTION_SEAT_ID}|${AUCTION_PRICE}";
        assertThat(macros.substitute(in, ctx)).isEqualTo("req-1|bid-9|imp-1|seat-a|2.5");
    }

    @Test
    void priceIsPlainDecimal() {
        assertThat(macros.substitute("${AUCTION_PRICE}", new MacroContext("r", "b", "i", "s", 0.1)))
                .isEqualTo("0.1");
    }

    @Test
    void nullTemplateStaysNull() {
        assertThat(macros.substitute(null, ctx)).isNull();
    }
}
