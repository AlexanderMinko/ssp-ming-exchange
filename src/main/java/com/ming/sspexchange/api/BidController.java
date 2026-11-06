package com.ming.sspexchange.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.service.auction.RtbOrchestrator;
import com.ming.sspexchange.service.auction.RtbOutcome;

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
        // value resolves to an unknown source (403) via SupplyResolver, not Spring's default 400.
        return switch (orchestrator.run(sourceId, request)) {
            case RtbOutcome.Fill(var response) -> ResponseEntity.ok(response);
            case RtbOutcome.NoBid _ -> ResponseEntity.noContent().build();
            case RtbOutcome.Rejected(var reason) -> ResponseEntity.status(reason.getHttpStatus()).build();
        };
    }
}
