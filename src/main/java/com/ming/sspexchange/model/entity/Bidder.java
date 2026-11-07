package com.ming.sspexchange.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One demand partner. Every bidder in the collection is called on every auction (no targeting yet). */
@Document("bidders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bidder {
    @Id
    private String id;
    private String name;
    private String seat;
    private String endpoint;   // path on the bidders service (e.g. /bid/alpha), joined to exchange.bidder.base-url
    private Integer tmaxMs;    // optional: shortens this bidder's deadline, never extends it
}
