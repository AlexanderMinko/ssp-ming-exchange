package com.ming.sspexchange.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.service.auction.AuctionOrchestrator;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BidController {

    private final AuctionOrchestrator orchestrator;

    @PostMapping(value = "/rtb/bid", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BidResponse> bid(
            @RequestParam(value = "account", required = false) String accountKey,
            @RequestBody BidRequest request) {
        // account is optional at the binding layer so a missing/blank value resolves to an unknown
        // account (403) via SupplyResolver, rather than Spring's default 400 for a missing param.
        return orchestrator.run(accountKey, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
