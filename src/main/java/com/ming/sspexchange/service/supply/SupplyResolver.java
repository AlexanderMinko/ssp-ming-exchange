package com.ming.sspexchange.service.supply;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.cache.SupplyPartnerCache;
import com.ming.sspexchange.model.RtbContext;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Publisher;

import lombok.RequiredArgsConstructor;

/**
 * sourceId (URL) → SOURCE; body publisher.id → PUBLISHER. Writes both into {@code RtbData}.
 * A registered publisher's config applies; an unknown or absent publisher leaves {@code publisher} null,
 * so the source's config applies.
 */
@Component
@RequiredArgsConstructor
public class SupplyResolver {

    static final int SOURCE_ID_LENGTH = 3;

    private final SupplyPartnerCache cache;

    /** Returns the reject reason, or empty when supply resolved into the context. */
    public Optional<RejectReason> resolve(RtbContext ctx) {
        var data = ctx.data();
        var sourceId = data.getSourceId();
        if (StringUtils.isBlank(sourceId)) {
            return Optional.of(RejectReason.MISSING_SOURCE);
        }
        if (sourceId.length() != SOURCE_ID_LENGTH) {
            return Optional.of(RejectReason.BAD_SOURCE_ID);
        }
        var source = cache.source(sourceId);
        if (source.isEmpty()) {
            return Optional.of(RejectReason.UNKNOWN_SOURCE);
        }

        data.setSource(source.get());
        data.setPublisher(extractPublisherId(ctx.adRequest())
                .flatMap(cache::publisher)
                .orElse(null));
        return Optional.empty();
    }

    private static Optional<String> extractPublisherId(BidRequest request) {
        var publisher = Objects.nonNull(request.getApp()) ? request.getApp().getPublisher()
                : Objects.nonNull(request.getSite()) ? request.getSite().getPublisher()
                : null;
        return Optional.ofNullable(publisher).map(Publisher::getId).filter(StringUtils::isNotBlank);
    }
}
