package com.nexus.bridgegateway.model;

import java.time.Instant;

public record BlockResponse(
    Long blockNumber,
    String timestamp
) {
    public BlockResponse(Long blockNumber) {
        this(blockNumber, Instant.now().toString());
    }
}
