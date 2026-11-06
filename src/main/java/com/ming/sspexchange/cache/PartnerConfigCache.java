package com.ming.sspexchange.cache;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.entity.SupplyPartnerType;
import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.repository.BidderRepository;
import com.ming.sspexchange.repository.SupplyPartnerRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerConfigCache {

    private final SupplyPartnerRepository suppliers;
    private final BidderRepository bidders;

    private volatile PartnerConfigSnapshot snapshot = PartnerConfigSnapshot.EMPTY;

    @PostConstruct
    void initialLoad() {
        // fail fast: an unreachable Mongo at startup aborts the context
        snapshot = load();
        log.info("Partner config loaded: {} sources, {} bidders",
                snapshot.sourcesById().size(), snapshot.bidders().size());
    }

    @Scheduled(fixedDelayString = "${exchange.config-refresh-ms:30000}",
               initialDelayString = "${exchange.config-refresh-ms:30000}")
    public void refresh() {
        try {
            snapshot = load();
        } catch (RuntimeException e) {
            log.warn("Config refresh failed, keeping last-good snapshot", e);
        }
    }

    private PartnerConfigSnapshot load() {
        var byType = suppliers.findAll().stream()
                .collect(partitioningBy(s -> s.getEntityType() == SupplyPartnerType.SOURCE));
        var sourcesById = byType.get(true).stream()
                .collect(toMap(SupplyPartner::getSourceId, identity()));
        var publishersById = byType.get(false).stream()
                .collect(toMap(SupplyPartner::getPublisherId, identity()));
        return new PartnerConfigSnapshot(sourcesById, publishersById,
                List.copyOf(bidders.findAll()));
    }

    public Optional<SupplyPartner> source(String sourceId) {
        return Optional.ofNullable(snapshot.sourcesById().get(sourceId));
    }

    public Optional<SupplyPartner> publisher(String publisherId) {
        return Optional.ofNullable(snapshot.publishersById().get(publisherId));
    }

    public List<Bidder> bidders() {
        return snapshot.bidders();
    }
}
