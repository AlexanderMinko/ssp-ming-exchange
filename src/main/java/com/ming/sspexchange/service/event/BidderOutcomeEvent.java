package com.ming.sspexchange.service.event;

import com.dslplatform.json.CompiledJson;

@CompiledJson
public record BidderOutcomeEvent(String name, String outcome, long latencyMs, Double price) { }
