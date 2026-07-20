package com.ming.sspexchange.model.openrtb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;

class OrtbRoundTripTest {

    private final DslJson<Object> dslJson = new DslJson<>(Settings.withRuntime()
            .allowArrayFormat(true)
            .skipDefaultValues(true)
            .includeServiceLoader());

    @Test
    void roundTripsFullBidRequestStably() throws IOException {
        byte[] json = getClass().getResourceAsStream("/fixtures/bid-request-full.json").readAllBytes();

        BidRequest first = dslJson.deserialize(BidRequest.class, json, json.length);

        assertThat(first.getId()).isEqualTo("req-1");
        assertThat(first.getTmax()).isEqualTo(350);
        assertThat(first.getImp()).hasSize(2);
        assertThat(first.getImp().get(0).getBanner().getW()).isEqualTo(320);
        assertThat(first.getImp().get(0).getBidfloor()).isEqualTo(0.5);
        assertThat(first.getImp().get(1).getVideo().getMimes()).containsExactly("video/mp4");
        assertThat(first.getApp().getPublisher().getId()).isEqualTo("pub-100");
        assertThat(first.getDevice().getGeo().getCountry()).isEqualTo("DEU");
        assertThat(first.getRegs().getExt()).containsEntry("us_privacy", "1---");
        assertThat(first.getExt()).containsKey("exchange");

        byte[] out = serialize(first);
        BidRequest second = dslJson.deserialize(BidRequest.class, out, out.length);
        assertThat(serialize(second)).isEqualTo(out);                       // stable round-trip
        assertThat(new String(out, StandardCharsets.UTF_8)).doesNotContain("null"); // nulls omitted
    }

    private byte[] serialize(Object o) throws IOException {
        var baos = new ByteArrayOutputStream();
        dslJson.serialize(o, baos);
        return baos.toByteArray();
    }
}
