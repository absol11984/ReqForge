package com.apishield.service;

import com.apishield.dto.ClientResponse;
import com.apishield.dto.CreateClientRequest;
import com.apishield.dto.UpdateClientRequest;
import com.apishield.entity.Client;
import com.apishield.entity.ClientStatus;
import com.apishield.exception.ClientNotFoundException;
import com.apishield.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository);
    }

    @Test
    void createClient() {
        when(clientRepository.existsByApiKey(anyString())).thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateClientRequest request = new CreateClientRequest("client-app");

        ClientResponse response = clientService.createClient(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("client-app");
        assertThat(response.apiKey()).startsWith("ask_live_");
        assertThat(response.status()).isEqualTo(ClientStatus.ACTIVE);
        verify(clientRepository).save(any(Client.class));
    }

    @Test
    void getClient() {
        UUID id = UUID.randomUUID();
        Client client = new Client(id, "client-app", "ask_live_testkey", ClientStatus.ACTIVE);

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));

        ClientResponse response = clientService.getClientById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("client-app");
        assertThat(response.apiKey()).isEqualTo("ask_live_testkey");
        assertThat(response.status()).isEqualTo(ClientStatus.ACTIVE);
    }

    @Test
    void updateClient() {
        UUID id = UUID.randomUUID();
        Client existing = new Client(id, "old-name", "ask_live_old", ClientStatus.INACTIVE);

        when(clientRepository.findById(id)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateClientRequest request = new UpdateClientRequest("updated-client", ClientStatus.ACTIVE);

        ClientResponse response = clientService.updateClient(id, request);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("updated-client");
        assertThat(response.status()).isEqualTo(ClientStatus.ACTIVE);
    }

    @Test
    void deleteClient() {
        UUID id = UUID.randomUUID();

        when(clientRepository.existsById(id)).thenReturn(true);
        doNothing().when(clientRepository).deleteById(id);

        clientService.deleteClient(id);

        verify(clientRepository).deleteById(id);
    }

    @Test
    void clientNotFound() {
        UUID id = UUID.randomUUID();

        when(clientRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClientById(id))
                .isInstanceOf(ClientNotFoundException.class)
                .hasMessageContaining("Client with id");
    }
}
