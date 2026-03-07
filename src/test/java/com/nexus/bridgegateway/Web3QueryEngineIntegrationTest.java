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
                .uri("/api/query/block/latest")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApiResponse.class)
                .value(response -> {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.message()).isEqualTo("success");
                    assertThat(response.data()).isNotNull();
                    
                    BlockResponse blockResponse = (BlockResponse) response.data();
                    assertThat(blockResponse.blockNumber()).isGreaterThan(0);
                    assertThat(blockResponse.timestamp()).isNotNull();
                });
    }

    @Test
    void testGetEtherBalance() {
        String testAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        
        webTestClient.get()
                .uri("/api/query/balance/{address}", testAddress)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApiResponse.class)
                .value(response -> {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.message()).isEqualTo("success");
                    assertThat(response.data()).isNotNull();
                    
                    BalanceResponse balanceResponse = (BalanceResponse) response.data();
                    assertThat(balanceResponse.address()).isEqualTo(testAddress);
                    assertThat(balanceResponse.balance()).isNotNull();
                    assertThat(balanceResponse.unit()).isEqualTo("ETH");
                });
    }

    @Test
    void testGetEtherBalanceWithInvalidAddress() {
        String invalidAddress = "invalid-address";
        
        webTestClient.get()
                .uri("/api/query/balance/{address}", invalidAddress)
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void testApiResponseStructure() {
        webTestClient.get()
                .uri("/api/query/block/latest")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("success")
                .jsonPath("$.data").isNotEmpty();
    }
}
