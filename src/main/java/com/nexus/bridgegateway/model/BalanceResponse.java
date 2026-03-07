package com.nexus.bridgegateway.model;

public record BalanceResponse(
    String address,
    String balance,
    String unit
) {}
