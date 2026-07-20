package com.ming.sspexchange.cache;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.entity.AccountEntity;
import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.entity.PublisherEntity;
import com.ming.sspexchange.repository.AccountRepository;
import com.ming.sspexchange.repository.BidderRepository;
import com.ming.sspexchange.repository.PublisherRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerConfigCache {

    private final AccountRepository accounts;
    private final PublisherRepository publishers;
    private final BidderRepository bidders;

    private volatile PartnerConfigSnapshot snapshot = PartnerConfigSnapshot.EMPTY;

    @PostConstruct
    void initialLoad() {
        // fail fast: an unreachable Mongo at startup aborts the context
        snapshot = load();
        log.info("Partner config loaded: {} accounts, {} bidders",
                snapshot.accountsByKey().size(), snapshot.bidders().size());
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
        var accountsByKey = accounts.findAll().stream()
                .collect(toMap(AccountEntity::getAccountKey, identity()));
        var publishersByAccount = publishers.findAll().stream()
                .collect(groupingBy(PublisherEntity::getAccountId,
                        toMap(PublisherEntity::getPublisherId, identity())));
        return new PartnerConfigSnapshot(accountsByKey, publishersByAccount, List.copyOf(bidders.findAll()));
    }

    public Optional<AccountEntity> accountByKey(String accountKey) {
        return Optional.ofNullable(snapshot.accountsByKey().get(accountKey));
    }

    public Optional<PublisherEntity> publisher(String accountId, String publisherId) {
        return Optional.ofNullable(
                snapshot.publishersByAccount().getOrDefault(accountId, Map.of()).get(publisherId));
    }

    public List<BidderEntity> bidders() {
        return snapshot.bidders();
    }
}
