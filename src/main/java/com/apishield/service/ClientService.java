package com.apishield.service;

import com.apishield.dto.ClientResponse;
import com.apishield.dto.CreateClientRequest;
import com.apishield.dto.UpdateClientRequest;
import com.apishield.entity.Client;
import com.apishield.entity.ClientStatus;
import com.apishield.entity.RateLimitAlgorithm;
import com.apishield.exception.ClientNotFoundException;
import com.apishield.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
public class ClientService {

    private static final String API_KEY_PREFIX = "ask_live_";

    private final ClientRepository clientRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional
    public ClientResponse createClient(CreateClientRequest request) {
        if (request == null || request.name() == null) {
            throw new IllegalArgumentException("name is required");
        }

        String apiKey = generateUniqueApiKey();
        Client client = new Client(
                UUID.randomUUID(),
                request.name(),
                apiKey,
                ClientStatus.ACTIVE,
                request.requestLimit(),
                request.windowSeconds(),
                request.algorithm()
        );
        Client saved = clientRepository.save(client);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> getAllClients() {
        return clientRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse getClientById(UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException(id));
        return toResponse(client);
    }

    @Transactional(readOnly = true)
    public Client getClientEntityByApiKey(String apiKey) {
        return clientRepository.findByApiKey(apiKey)
                .orElse(null);
    }

    @Transactional
    public ClientResponse updateClient(UUID id, UpdateClientRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException(id));

        client.setName(request.name());
        client.setStatus(request.status());
        client.setAlgorithm(request.algorithm() == null
                ? RateLimitAlgorithm.FIXED_WINDOW : request.algorithm());
        client.setRequestLimit(request.requestLimit());
        client.setWindowSeconds(request.windowSeconds());

        Client saved = clientRepository.save(client);
        return toResponse(saved);
    }

    @Transactional
    public void deleteClient(UUID id) {
        if (!clientRepository.existsById(id)) {
            throw new ClientNotFoundException(id);
        }
        clientRepository.deleteById(id);
    }

    private String generateUniqueApiKey() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String suffix = String.format("%016x", secureRandom.nextLong());
            String apiKey = API_KEY_PREFIX + suffix;
            if (!clientRepository.existsByApiKey(apiKey)) {
                return apiKey;
            }
        }
        throw new IllegalStateException("Failed to generate a unique API key after multiple attempts");
    }

    private ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getApiKey(),
                client.getStatus(),
                client.getRequestLimit(),
                client.getWindowSeconds(),
                client.getAlgorithm(),
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }
}
