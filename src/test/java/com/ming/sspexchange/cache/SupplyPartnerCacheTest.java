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

import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.model.entity.SupplyPartnerType;
import com.ming.sspexchange.repository.SupplyPartnerRepository;

@SpringBootTest
@Testcontainers
class SupplyPartnerCacheTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired SupplyPartnerRepository repository;
    @Autowired SupplyPartnerCache cache;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void keysSourcesBySourceIdAndPublishersByPublisherId() {
        repository.save(SupplyPartner.builder().id("s-1").entityType(SupplyPartnerType.SOURCE)
                .sourceId("abc").name("Source abc").build());
        repository.save(SupplyPartner.builder().id("p-1").entityType(SupplyPartnerType.PUBLISHER)
                .publisherId("pub-100").name("Pub 100").build());

        cache.refresh();

        assertThat(cache.source("abc")).isPresent();
        assertThat(cache.source("zzz")).isEmpty();
        assertThat(cache.publisher("pub-100")).isPresent();
        assertThat(cache.publisher("pub-999")).isEmpty();
        assertThat(cache.source("pub-100")).as("a publisher id is not a source id").isEmpty();
    }

    @Test
    void refreshReplacesTheWholeSnapshot() {
        repository.save(SupplyPartner.builder().id("s-1").entityType(SupplyPartnerType.SOURCE)
                .sourceId("abc").name("A").build());
        cache.refresh();
        assertThat(cache.source("abc")).isPresent();

        repository.deleteAll();
        cache.refresh();

        assertThat(cache.source("abc")).as("a deleted partner disappears on the next refresh").isEmpty();
    }
}
