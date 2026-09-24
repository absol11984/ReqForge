package com.apishield.service;

import com.apishield.dto.ClientResponse;
import com.apishield.dto.CreateClientRequest;
import com.apishield.dto.UpdateClientRequest;
import com.apishield.entity.ClientStatus;
import com.apishield.entity.RateLimitAlgorithm;
import com.apishield.exception.ClientNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ClientServiceIntegrationTest {

    @Autowired
    private ClientService clientService;

    @Test
    void createClient_persistsAndReturnsApiKey() {
        ClientResponse created = clientService.createClient(new CreateClientRequest("client-app"));

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("client-app");
        assertThat(created.apiKey()).startsWith("ask_live_");
        assertThat(created.status()).isEqualTo(ClientStatus.ACTIVE);
        // Phase 2 fields
        assertThat(created.requestLimit()).isEqualTo(100);
        assertThat(created.windowSeconds()).isEqualTo(60);
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();
        // Phase 4 defaulting
        assertThat(created.algorithm()).isEqualTo(RateLimitAlgorithm.FIXED_WINDOW);
    }

    @Test
    void createClient_persistsAlgorithm_forAllSupportedValues() {
        ClientResponse fixed = clientService.createClient(
                new CreateClientRequest("fixed-client", 5, 60, RateLimitAlgorithm.FIXED_WINDOW)
        );
        ClientResponse sliding = clientService.createClient(
                new CreateClientRequest("sliding-client", 5, 60, RateLimitAlgorithm.SLIDING_WINDOW)
        );
        ClientResponse bucket = clientService.createClient(
                new CreateClientRequest("bucket-client", 5, 60, RateLimitAlgorithm.TOKEN_BUCKET)
        );

        assertThat(clientService.getClientById(fixed.id()).algorithm()).isEqualTo(RateLimitAlgorithm.FIXED_WINDOW);
        assertThat(clientService.getClientById(sliding.id()).algorithm()).isEqualTo(RateLimitAlgorithm.SLIDING_WINDOW);
        assertThat(clientService.getClientById(bucket.id()).algorithm()).isEqualTo(RateLimitAlgorithm.TOKEN_BUCKET);
    }

    @Test
    void updateClient_persistsAlgorithm_andDefaultsWhenOmitted() {
        ClientResponse created = clientService.createClient(new CreateClientRequest("client-app"));

        ClientResponse updatedSliding = clientService.updateClient(
                created.id(),
                new UpdateClientRequest(
                        "updated-client",
                        ClientStatus.ACTIVE,
                        10,
                        120,
                        RateLimitAlgorithm.SLIDING_WINDOW
                )
        );
        assertThat(updatedSliding.algorithm()).isEqualTo(RateLimitAlgorithm.SLIDING_WINDOW);
        assertThat(clientService.getClientById(created.id()).algorithm()).isEqualTo(RateLimitAlgorithm.SLIDING_WINDOW);

        // Now simulate "omitted" algorithm by passing null.
        ClientResponse updatedDefault = clientService.updateClient(
                created.id(),
                new UpdateClientRequest(
                        "updated-client-2",
                        ClientStatus.INACTIVE,
                        10,
                        120,
                        null
                )
        );
        assertThat(updatedDefault.algorithm()).isEqualTo(RateLimitAlgorithm.FIXED_WINDOW);
    }

    @Test
    void getClient_updateClient_deleteClient() {
        ClientResponse created = clientService.createClient(new CreateClientRequest("client-app"));

        ClientResponse fetched = clientService.getClientById(created.id());
        assertThat(fetched.name()).isEqualTo("client-app");
        assertThat(fetched.requestLimit()).isEqualTo(100);
        assertThat(fetched.windowSeconds()).isEqualTo(60);

        ClientResponse updated = clientService.updateClient(
                created.id(),
                new UpdateClientRequest("updated-client", ClientStatus.INACTIVE)
        );
        assertThat(updated.name()).isEqualTo("updated-client");
        assertThat(updated.status()).isEqualTo(ClientStatus.INACTIVE);
        assertThat(updated.requestLimit()).isEqualTo(100);
        assertThat(updated.windowSeconds()).isEqualTo(60);

        clientService.deleteClient(created.id());
        assertThatThrownBy(() -> clientService.getClientById(created.id()))
                .isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    void clientNotFound() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> clientService.getClientById(id))
                .isInstanceOf(ClientNotFoundException.class);
    }
}
