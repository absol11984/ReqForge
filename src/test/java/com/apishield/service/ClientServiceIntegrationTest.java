package com.apishield.service;

import com.apishield.dto.ClientResponse;
import com.apishield.dto.CreateClientRequest;
import com.apishield.dto.UpdateClientRequest;
import com.apishield.entity.ClientStatus;
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
        // Phase 2 fields from UpdateClientRequest 2-arg constructor
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