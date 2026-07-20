package com.ming.sspexchange.model.entity;

import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document("bidders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidderEntity {
    @Id
    private String id;
    private String name;
    private String seat;
    private String endpoint;   // absolute URL, or "mock:<name>" for built-in mock demand
    private PartnerStatus status;
    private Targeting targeting;
    private Integer tmaxMs;
    private MockConfig mock;

    public boolean isMock() {
        return Objects.nonNull(endpoint) && endpoint.startsWith("mock:");
    }
}
