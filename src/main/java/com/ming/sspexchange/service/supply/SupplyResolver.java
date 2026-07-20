package com.ming.sspexchange.service.supply;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.cache.PartnerConfigCache;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Publisher;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SupplyResolver {

    private final PartnerConfigCache cache;

    public SupplyContext resolve(String accountKey, BidRequest request) {
        var account = cache.accountByKey(accountKey)
                .filter(a -> a.getStatus() == PartnerStatus.ACTIVE)
                .orElseThrow(() -> new SupplyAuthException("unknown or inactive account"));

        var publisherId = extractPublisherId(request)
                .orElseThrow(() -> new InvalidBidRequestException("missing app/site publisher.id"));

        var publisher = cache.publisher(account.getId(), publisherId)
                .filter(p -> p.getStatus() == PartnerStatus.ACTIVE)
                .orElseThrow(() -> new SupplyAuthException("unknown or inactive publisher"));

        return new SupplyContext(account, publisher);
    }

    private Optional<String> extractPublisherId(BidRequest r) {
        var pub = Objects.nonNull(r.getApp()) ? r.getApp().getPublisher()
                : Objects.nonNull(r.getSite()) ? r.getSite().getPublisher()
                : null;
        return Optional.ofNullable(pub).map(Publisher::getId).filter(id -> !id.isBlank());
    }
}
