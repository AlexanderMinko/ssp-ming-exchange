package com.ming.sspexchange.cache;

import java.util.List;
import java.util.Map;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.entity.SupplyPartner;

public record PartnerConfigSnapshot(
        Map<String, SupplyPartner> sourcesById,
        Map<String, SupplyPartner> publishersById,
        List<Bidder> bidders) {

    public static final PartnerConfigSnapshot EMPTY =
            new PartnerConfigSnapshot(Map.of(), Map.of(), List.of());
}
