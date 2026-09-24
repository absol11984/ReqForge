package com.apishield.controller;

import com.apishield.dto.ClientResponse;
import com.apishield.dto.CreateClientRequest;
import com.apishield.dto.UpdateClientRequest;
import com.apishield.entity.ClientStatus;
import com.apishield.exception.ClientNotFoundException;
import com.apishield.service.ClientService;
import com.apishield.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClientService clientService;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void createClient() throws Exception {
        UUID id = UUID.randomUUID();
        ClientResponse response = new ClientResponse(
                id,
                "client-app",
                "ask_live_key",
                ClientStatus.ACTIVE,
                100,  // requestLimit
                60,   // windowSeconds
                Instant.parse("2026-09-23T19:00:00Z"),
                Instant.parse("2026-09-23T19:00:00Z")
        );

        when(clientService.createClient(any(CreateClientRequest.class))).thenReturn(response);

        CreateClientRequest request = new CreateClientRequest("client-app");

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.name", is("client-app")))
                .andExpect(jsonPath("$.apiKey", is("ask_live_key")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.requestLimit", is(100)))
                .andExpect(jsonPath("$.windowSeconds", is(60)));
    }

    @Test
    void getAllClients() throws Exception {
        ClientResponse c1 = new ClientResponse(
                UUID.randomUUID(),
                "client-1",
                "ask_live_1",
                ClientStatus.ACTIVE,
                100, 60,
                Instant.parse("2026-09-23T19:00:00Z"),
                Instant.parse("2026-09-23T19:00:00Z")
        );
        ClientResponse c2 = new ClientResponse(
                UUID.randomUUID(),
                "client-2",
                "ask_live_2",
                ClientStatus.INACTIVE,
                50, 30,
                Instant.parse("2026-09-23T19:01:00Z"),
                Instant.parse("2026-09-23T19:01:00Z")
        );

        when(clientService.getAllClients()).thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("client-1")))
                .andExpect(jsonPath("$[1].name", is("client-2")));
    }

    @Test
    void getClientById() throws Exception {
        UUID id = UUID.randomUUID();
        ClientResponse response = new ClientResponse(
                id,
                "client-app",
                "ask_live_key",
                ClientStatus.ACTIVE,
                100, 60,
                Instant.parse("2026-09-23T19:00:00Z"),
                Instant.parse("2026-09-23T19:00:00Z")
        );

        when(clientService.getClientById(eq(id))).thenReturn(response);

        mockMvc.perform(get("/api/clients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.name", is("client-app")));
    }

    @Test
    void updateClient() throws Exception {
        UUID id = UUID.randomUUID();
        ClientResponse response = new ClientResponse(
                id,
                "updated-client",
                "ask_live_key",
                ClientStatus.ACTIVE,
                100, 60,
                Instant.parse("2026-09-23T19:00:00Z"),
                Instant.parse("2026-09-23T19:10:00Z")
        );

        when(clientService.updateClient(eq(id), any(UpdateClientRequest.class))).thenReturn(response);

        UpdateClientRequest request = new UpdateClientRequest("updated-client", ClientStatus.ACTIVE);

        mockMvc.perform(put("/api/clients/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("updated-client")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void deleteClient() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/clients/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void getClientNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.getClientById(eq(id))).thenThrow(new ClientNotFoundException(id));

        mockMvc.perform(get("/api/clients/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Client Not Found")))
                .andExpect(jsonPath("$.path", containsString("/api/clients/")));
    }
}