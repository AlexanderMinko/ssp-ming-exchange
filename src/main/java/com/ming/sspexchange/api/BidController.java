package com.ming.sspexchange.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.service.RtbOrchestrator;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BidController {

    private final RtbOrchestrator orchestrator;

    @PostMapping(value = "/rtb/bid", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BidResponse> bid(
            @RequestParam(value = "account", required = false) String sourceId,
            @RequestBody BidRequest request) {
        // account carries the 3-char sourceId. It is optional at the binding layer so a missing/blank
        // value becomes a supply reject in SupplyResolver (204), not Spring's default 400.
        // Fill → 200 with the response. No-bid or reject → 204, empty body.
        return orchestrator.run(sourceId, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
