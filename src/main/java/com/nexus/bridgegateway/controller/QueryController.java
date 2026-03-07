package com.nexus.bridgegateway.controller;

import com.nexus.bridgegateway.model.ApiResponse;
import com.nexus.bridgegateway.model.BalanceResponse;
import com.nexus.bridgegateway.model.BlockResponse;
import com.nexus.bridgegateway.service.Web3QueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/query")
public class QueryController {

    private final Web3QueryService web3QueryService;

    public QueryController(Web3QueryService web3QueryService) {
        this.web3QueryService = web3QueryService;
    }

    @GetMapping("/balance/{address}")
    public ApiResponse<BalanceResponse> getEtherBalance(@PathVariable String address) {
        BalanceResponse balanceResponse = web3QueryService.getEtherBalance(address);
        return ApiResponse.success(balanceResponse);
    }

    @GetMapping("/block/latest")
    public ApiResponse<BlockResponse> getLatestBlockNumber() {
        BlockResponse blockResponse = web3QueryService.getLatestBlockNumber();
        return ApiResponse.success(blockResponse);
    }
}
