package com.ming.sspexchange.service.demand;

final class Latency {

    private Latency() { }

    static long msSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
