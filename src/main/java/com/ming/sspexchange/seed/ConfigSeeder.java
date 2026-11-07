package com.ming.sspexchange.seed;

import static com.ming.sspexchange.model.entity.SupplyPartnerType.PUBLISHER;
import static com.ming.sspexchange.model.entity.SupplyPartnerType.SOURCE;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.cache.BidderCache;
import com.ming.sspexchange.cache.SupplyPartnerCache;
import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.repository.BidderRepository;
import com.ming.sspexchange.repository.SupplyPartnerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds baseline partner config at startup — the in-process replacement for the old mongoimport
 * seed Job. Inserts each document only if its _id is absent, so it never clobbers later edits and
 * is safe under multiple pods (a concurrent double-insert just re-saves identical data). Refreshes
 * the cache afterward so a freshly deployed pod serves fills immediately.
 *
 * <p>Before seeding it ensures a unique index on {@code publisherId}. The index is partial (PUBLISHER
 * documents only), because sources have no publisherId. Creating an existing index is a no-op. If the
 * collection already holds a duplicate publisherId, index creation fails and the app does not start.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigSeeder implements ApplicationRunner {

    private final SupplyPartnerRepository suppliers;
    private final BidderRepository bidders;
    private final SupplyPartnerCache supplyPartnerCache;
    private final BidderCache bidderCache;
    private final MongoTemplate mongo;

    @Override
    public void run(ApplicationArguments args) {
        ensurePublisherIdUnique();
        int inserted = insertMissing(suppliers, seedSuppliers(), SupplyPartner::getId)
                + insertMissing(bidders, seedBidders(), Bidder::getId);
        if (inserted > 0) {
            log.info("ConfigSeeder inserted {} missing config document(s); refreshing caches", inserted);
            supplyPartnerCache.refresh();
            bidderCache.refresh();
        } else {
            log.info("ConfigSeeder: baseline config already present, nothing to insert");
        }
    }

    private void ensurePublisherIdUnique() {
        mongo.indexOps(SupplyPartner.class).createIndex(new Index()
                .on("publisherId", Sort.Direction.ASC)
                .named("publisherId_unique")
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("entityType").is(PUBLISHER.name()))));
    }

    private static <T> int insertMissing(MongoRepository<T, String> repo, List<T> items, Function<T, String> id) {
        var missing = items.stream()
                .filter(Predicate.not(item -> repo.existsById(id.apply(item))))
                .toList();
        repo.saveAll(missing);
        return missing.size();
    }

    private static List<SupplyPartner> seedSuppliers() {
        return List.of(
                SupplyPartner.builder().id("src-doc-1").entityType(SOURCE).sourceId("amz")
                        .name("Amazon APS source").build(),
                SupplyPartner.builder().id("src-doc-2").entityType(SOURCE).sourceId("lft")
                        .name("Liftoff source").build(),
                SupplyPartner.builder().id("pub-doc-1").entityType(PUBLISHER)
                        .publisherId("pub-100").name("News App Pub").build(),
                SupplyPartner.builder().id("pub-doc-3").entityType(PUBLISHER)
                        .publisherId("pub-300").name("Game Pub").build());
    }

    // Endpoints are paths on the bidders service (ssp-ming-bidders). gamma is seeded on purpose: its
    // latency there exceeds the deadline, so it exercises the TIMEOUT path.
    private static List<Bidder> seedBidders() {
        return List.of(
                Bidder.builder().id("bidder-alpha")
                        .name("alpha")
                        .seat("seat-alpha")
                        .endpoint("/bid/alpha").build(),
                Bidder.builder().id("bidder-beta").name("beta").seat("seat-beta")
                        .endpoint("/bid/beta").tmaxMs(200).build(),
                Bidder.builder().id("bidder-gamma").name("gamma").seat("seat-gamma")
                        .endpoint("/bid/gamma").build());
    }
}
