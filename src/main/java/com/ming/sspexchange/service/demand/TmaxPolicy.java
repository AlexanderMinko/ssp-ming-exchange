package com.ming.sspexchange.service.demand;

import java.util.Objects;

public final class TmaxPolicy {

    public static final int DEFAULT_TMAX_MS = 300;
    public static final int MIN_TMAX_MS = 100;
    public static final int MAX_TMAX_MS = 1000;
    public static final int OVERHEAD_MS = 50;

    private TmaxPolicy() { }

    public static int effective(Integer requested) {
        return Math.clamp(Objects.nonNull(requested) ? requested : DEFAULT_TMAX_MS, MIN_TMAX_MS, MAX_TMAX_MS);
    }

    public static int outbound(int effectiveTmax, Integer bidderOverrideMs) {
        int budget = effectiveTmax - OVERHEAD_MS;
        return Objects.nonNull(bidderOverrideMs) ? Math.min(budget, bidderOverrideMs) : budget;
    }
}
