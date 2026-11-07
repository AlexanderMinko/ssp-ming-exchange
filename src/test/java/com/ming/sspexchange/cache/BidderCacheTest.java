package com.ming.sspexchange.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.repository.BidderRepository;

@SpringBootTest
@Testcontainers
class BidderCacheTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired BidderRepository repository;
    @Autowired BidderCache cache;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void servesEveryBidderFromTheRefreshedList() {
        repository.save(Bidder.builder().id("b-1").name("alpha").seat("seat-alpha").endpoint("/bid/alpha").build());
        repository.save(Bidder.builder().id("b-2").name("beta").seat("seat-beta").endpoint("/bid/beta").build());

        cache.refresh();

        assertThat(cache.all()).extracting(Bidder::getName).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void refreshReplacesTheWholeList() {
        repository.save(Bidder.builder().id("b-1").name("alpha").seat("seat-alpha").endpoint("/bid/alpha").build());
        cache.refresh();
        assertThat(cache.all()).hasSize(1);

        repository.deleteAll();
        cache.refresh();

        assertThat(cache.all()).as("a deleted bidder disappears on the next refresh").isEmpty();
    }
}
