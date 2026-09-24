package com.apishield.repository;

import com.apishield.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    boolean existsByApiKey(String apiKey);

    Optional<Client> findByApiKey(String apiKey);
}
