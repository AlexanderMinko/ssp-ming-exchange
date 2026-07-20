package com.ming.sspexchange.cache;

import java.util.List;
import java.util.Map;

import com.ming.sspexchange.model.entity.AccountEntity;
import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.entity.PublisherEntity;

public record PartnerConfigSnapshot(
        Map<String, AccountEntity> accountsByKey,
        Map<String, Map<String, PublisherEntity>> publishersByAccount,
        List<BidderEntity> bidders) {

    public static final PartnerConfigSnapshot EMPTY =
            new PartnerConfigSnapshot(Map.of(), Map.of(), List.of());
}
