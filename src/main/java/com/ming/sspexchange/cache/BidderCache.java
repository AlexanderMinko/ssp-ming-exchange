package com.ming.sspexchange.cache;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.repository.BidderRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory bidders, so the request path never touches Mongo. The whole list is swapped on every
 * refresh; a failed refresh keeps the last good one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidderCache {

    private final BidderRepository repository;

    private volatile List<Bidder> bidders = List.of();

    @PostConstruct
    void initialLoad() {
        // fail fast: an unreachable Mongo at startup aborts the context
        bidders = load();
        log.info("Bidders loaded: {}", bidders.size());
    }

    @Scheduled(fixedDelayString = "${exchange.config-refresh-ms:30000}",
               initialDelayString = "${exchange.config-refresh-ms:30000}")
    public void refresh() {
        try {
            bidders = load();
        } catch (RuntimeException exception) {
            log.warn("Bidder refresh failed, keeping last-good list", exception);
        }
    }

    public List<Bidder> all() {
        return bidders;
    }

    private List<Bidder> load() {
        return List.copyOf(repository.findAll());
    }
}
