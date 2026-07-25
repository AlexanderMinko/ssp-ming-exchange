package com.ming.sspexchange.seed;

import static com.ming.sspexchange.model.entity.AdFormat.BANNER;
import static com.ming.sspexchange.model.entity.AdFormat.VIDEO;
import static com.ming.sspexchange.model.entity.PartnerStatus.ACTIVE;
import static com.ming.sspexchange.model.entity.PartnerStatus.PAUSED;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.cache.PartnerConfigCache;
import com.ming.sspexchange.model.entity.AccountEntity;
import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.entity.MockConfig;
import com.ming.sspexchange.model.entity.PublisherEntity;
import com.ming.sspexchange.model.entity.Targeting;
import com.ming.sspexchange.repository.AccountRepository;
import com.ming.sspexchange.repository.BidderRepository;
import com.ming.sspexchange.repository.PublisherRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds baseline partner config at startup — the in-process replacement for the old mongoimport
 * seed Job. Inserts each document only if its _id is absent, so it never clobbers later edits and
 * is safe under multiple pods (a concurrent double-insert just re-saves identical data). Refreshes
 * the cache afterward so a freshly deployed pod serves fills immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigSeeder implements ApplicationRunner {

    private final AccountRepository accounts;
    private final PublisherRepository publishers;
    private final BidderRepository bidders;
    private final PartnerConfigCache cache;

    @Override
    public void run(ApplicationArguments args) {
        int inserted = insertMissing(accounts, seedAccounts(), AccountEntity::getId)
                + insertMissing(publishers, seedPublishers(), PublisherEntity::getId)
                + insertMissing(bidders, seedBidders(), BidderEntity::getId);
        if (inserted > 0) {
            log.info("ConfigSeeder inserted {} missing config document(s); refreshing cache", inserted);
            cache.refresh();
        } else {
            log.info("ConfigSeeder: baseline config already present, nothing to insert");
        }
    }

    private static <T> int insertMissing(MongoRepository<T, String> repo, List<T> items, Function<T, String> id) {
        int n = 0;
        for (T item : items) {
            if (!repo.existsById(id.apply(item))) {
                repo.save(item);
                n++;
            }
        }
        return n;
    }

    private static List<AccountEntity> seedAccounts() {
        return List.of(
                AccountEntity.builder().id("acc-amz").accountKey("amz-key-001")
                        .name("Amazon APS").type("API").status(ACTIVE).build(),
                AccountEntity.builder().id("acc-liftoff").accountKey("liftoff-key-001")
                        .name("Liftoff").type("SDK").status(ACTIVE).build());
    }

    private static List<PublisherEntity> seedPublishers() {
        return List.of(
                PublisherEntity.builder().id("pub-doc-1").accountId("acc-amz").publisherId("pub-100")
                        .name("News App Pub").status(ACTIVE).build(),
                PublisherEntity.builder().id("pub-doc-2").accountId("acc-amz").publisherId("pub-200")
                        .name("Paused Pub").status(PAUSED).build(),
                PublisherEntity.builder().id("pub-doc-3").accountId("acc-liftoff").publisherId("pub-300")
                        .name("Game Pub").status(ACTIVE).build());
    }

    private static List<BidderEntity> seedBidders() {
        return List.of(
                BidderEntity.builder().id("bidder-alpha").name("alpha").seat("seat-alpha")
                        .endpoint("mock:alpha").status(ACTIVE)
                        .targeting(Targeting.builder().formats(Set.of(BANNER, VIDEO)).countries(Set.of()).build())
                        .mock(MockConfig.builder().price(2.5).latencyMs(20).bidRate(1.0).build())
                        .build(),
                BidderEntity.builder().id("bidder-beta").name("beta").seat("seat-beta")
                        .endpoint("mock:beta").status(ACTIVE).tmaxMs(200)
                        .targeting(Targeting.builder().formats(Set.of(BANNER)).countries(Set.of("USA")).build())
                        .mock(MockConfig.builder().price(1.2).latencyMs(50).bidRate(0.8).build())
                        .build(),
                BidderEntity.builder().id("bidder-gamma").name("gamma").seat("seat-gamma")
                        .endpoint("http://dsp.dsp.svc.cluster.local:8080/rtb/bid").status(PAUSED)
                        .targeting(Targeting.builder().formats(Set.of(BANNER, VIDEO)).countries(Set.of()).build())
                        .build());
    }
}
