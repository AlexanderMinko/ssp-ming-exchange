package com.ming.sspexchange.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One collection for both supply levels. A SOURCE is keyed by its 3-char {@code sourceId} (the
 * {@code ?account=} URL param). A PUBLISHER is keyed by its global
 * {@code publisherId}, matched against the request body. It has no DB link to a source: a source's
 * traffic carries the publisher id, and the source's config applies when that publisher is not registered.
 */
@Document("supply_partners")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyPartner {
    @Id
    private String id;
    private SupplyPartnerType entityType;
    private String publisherId;   // PUBLISHER only, globally unique
    private String sourceId;      // SOURCE only, always 3 chars
    private String name;
}
