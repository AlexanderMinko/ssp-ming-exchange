package com.ming.sspexchange.cache;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.model.entity.SupplyPartnerType;
import com.ming.sspexchange.repository.SupplyPartnerRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory supply partners keyed the way the request path looks them up: sources by the 3-char
 * {@code sourceId}, publishers by {@code publisherId}. The whole snapshot is swapped on every refresh;
 * a failed refresh keeps the last good one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplyPartnerCache {

    private final SupplyPartnerRepository repository;

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    @PostConstruct
    void initialLoad() {
        // fail fast: an unreachable Mongo at startup aborts the context
        snapshot = load();
        log.info("Supply partners loaded: {} sources, {} publishers",
                snapshot.sourcesById().size(), snapshot.publishersById().size());
    }

    @Scheduled(fixedDelayString = "${exchange.config-refresh-ms:30000}",
               initialDelayString = "${exchange.config-refresh-ms:30000}")
    public void refresh() {
        try {
            snapshot = load();
        } catch (RuntimeException exception) {
            log.warn("Supply partner refresh failed, keeping last-good snapshot", exception);
        }
    }

    public Optional<SupplyPartner> source(String sourceId) {
        return Optional.ofNullable(snapshot.sourcesById().get(sourceId));
    }

    public Optional<SupplyPartner> publisher(String publisherId) {
        return Optional.ofNullable(snapshot.publishersById().get(publisherId));
    }

    private Snapshot load() {
        var byType = repository.findAll().stream()
                .collect(partitioningBy(partner -> partner.getEntityType() == SupplyPartnerType.SOURCE));
        var sourcesById = byType.get(true).stream()
                .collect(toMap(SupplyPartner::getSourceId, identity()));
        var publishersById = byType.get(false).stream()
                .collect(toMap(SupplyPartner::getPublisherId, identity()));
        return new Snapshot(sourcesById, publishersById);
    }

    private record Snapshot(Map<String, SupplyPartner> sourcesById, Map<String, SupplyPartner> publishersById) {

        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
    }
}
