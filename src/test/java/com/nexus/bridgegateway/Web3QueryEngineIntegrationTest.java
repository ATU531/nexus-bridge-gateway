package com.nexus.bridgegateway;

import com.nexus.bridgegateway.model.ApiResponse;
import com.nexus.bridgegateway.model.BalanceResponse;
import com.nexus.bridgegateway.model.BlockResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class Web3QueryEngineIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testGetLatestBlockNumber() {
        webTestClient.get()
                .uri("/api/v1/query/eth/block/latest")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApiResponse.class)
                .value(response -> {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.message()).isEqualTo("success");
                    assertThat(response.data()).isNotNull();
                    
                    BlockResponse blockResponse = (BlockResponse) response.data();
                    assertThat(blockResponse.blockNumber()).isGreaterThan(0);
                });
    }

    @Test
    void testGetEtherBalance() {
        String testAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        
        webTestClient.get()
                .uri("/api/v1/query/eth/balance/{address}", testAddress)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApiResponse.class)
                .value(response -> {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.message()).isEqualTo("success");
                    assertThat(response.data()).isNotNull();
                    
                    BalanceResponse balanceResponse = (BalanceResponse) response.data();
                    assertThat(balanceResponse.address()).isEqualTo(testAddress);
                    assertThat(balanceResponse.chain()).isEqualTo("eth");
                    assertThat(balanceResponse.symbol()).isEqualTo("ETH");
                    assertThat(balanceResponse.balance()).isNotNull();
                });
    }

    @Test
    void testGetBscBalance() {
        String testAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        
        webTestClient.get()
                .uri("/api/v1/query/bsc/balance/{address}", testAddress)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApiResponse.class)
                .value(response -> {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.message()).isEqualTo("success");
                    assertThat(response.data()).isNotNull();
                    
                    BalanceResponse balanceResponse = (BalanceResponse) response.data();
                    assertThat(balanceResponse.address()).isEqualTo(testAddress);
                    assertThat(balanceResponse.chain()).isEqualTo("bsc");
                    assertThat(balanceResponse.symbol()).isEqualTo("BNB");
                    assertThat(balanceResponse.balance()).isNotNull();
                });
    }

    @Test
    void testGetPolygonBalance() {
        String testAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        
        webTestClient.get()
                .uri("/api/v1/query/polygon/balance/{address}", testAddress)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApiResponse.class)
                .value(response -> {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.message()).isEqualTo("success");
                    assertThat(response.data()).isNotNull();
                    
                    BalanceResponse balanceResponse = (BalanceResponse) response.data();
                    assertThat(balanceResponse.address()).isEqualTo(testAddress);
                    assertThat(balanceResponse.chain()).isEqualTo("polygon");
                    assertThat(balanceResponse.symbol()).isEqualTo("MATIC");
                    assertThat(balanceResponse.balance()).isNotNull();
                });
    }

    @Test
    void testGetEtherBalanceWithInvalidAddress() {
        String invalidAddress = "invalid-address";
        
        webTestClient.get()
                .uri("/api/v1/query/eth/balance/{address}", invalidAddress)
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void testUnsupportedChain() {
        String testAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        
        webTestClient.get()
                .uri("/api/v1/query/unsupported/balance/{address}", testAddress)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testApiResponseStructure() {
        webTestClient.get()
                .uri("/api/v1/query/eth/block/latest")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("success")
                .jsonPath("$.data").isNotEmpty();
    }
}
