package com.ming.sspexchange.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockConfig {
    private Double price;      // fixed CPM of fabricated bids (required for mock bidders)
    private Integer latencyMs; // simulated response latency; null = 0
    private Double bidRate;    // 0.0-1.0 probability of bidding; null = 1.0
}
