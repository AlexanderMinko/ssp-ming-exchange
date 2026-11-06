package com.ming.sspexchange.service.supply;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.cache.PartnerConfigCache;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Publisher;
import com.ming.sspexchange.model.RtbContext;

import lombok.RequiredArgsConstructor;

/**
 * sourceId (URL) → SOURCE; body publisher.id → PUBLISHER under that sourceId. Writes both
 * into {@code RtbData}. A registered publisher's config applies (a paused one rejects the request);
 * an unknown or absent publisher leaves {@code publisher} null, so the source's config applies.
 */
@Component
@RequiredArgsConstructor
public class SupplyResolver {

    static final int SOURCE_ID_LENGTH = 3;

    private final PartnerConfigCache cache;

    /** Returns the reject reason, or empty when supply resolved into {@code ctx.data()}. */
    public Optional<RejectReason> resolve(RtbContext ctx) {
        var data = ctx.data();
        var sourceId = data.getSourceId();
        if (StringUtils.isBlank(sourceId)) {
            return Optional.of(RejectReason.MISSING_SOURCE);
        }
        if (sourceId.length() != SOURCE_ID_LENGTH) {
            return Optional.of(RejectReason.BAD_SOURCE_ID);
        }

        var source = cache.source(sourceId)
                .filter(s -> s.getStatus() == PartnerStatus.ACTIVE);
        if (source.isEmpty()) {
            return Optional.of(RejectReason.UNKNOWN_SOURCE);
        }

        var publisher = extractPublisherId(ctx.adRequest())
                .flatMap(id -> cache.publisher(id));
        if (publisher.isPresent() && publisher.get().getStatus() != PartnerStatus.ACTIVE) {
            return Optional.of(RejectReason.PAUSED_PUBLISHER);
        }

        data.setSource(source.get());
        data.setPublisher(publisher.orElse(null));
        return Optional.empty();
    }

    private Optional<String> extractPublisherId(BidRequest r) {
        var pub = Objects.nonNull(r.getApp()) ? r.getApp().getPublisher()
                : Objects.nonNull(r.getSite()) ? r.getSite().getPublisher()
                : null;
        return Optional.ofNullable(pub).map(Publisher::getId).filter(StringUtils::isNotBlank);
    }
}
