package com.apishield.controller;

import com.apishield.dto.ClientResponse;
import com.apishield.dto.CreateClientRequest;
import com.apishield.dto.UpdateClientRequest;
import com.apishield.exception.ClientNotFoundException;
import com.apishield.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Clients", description = "Client registration and management")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping("/clients")
    @Operation(summary = "Create a client")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Client created",
                    content = @Content(schema = @Schema(implementation = ClientResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request",
                    content = @Content(schema = @Schema(implementation = com.apishield.exception.ErrorResponse.class)))
    })
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody CreateClientRequest request
    ) {
        ClientResponse created = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/clients")
    @Operation(summary = "Get all clients")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clients retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ClientResponse.class))))
    })
    public ResponseEntity<List<ClientResponse>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients());
    }

    @GetMapping("/clients/{id}")
    @Operation(summary = "Get a client by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client retrieved",
                    content = @Content(schema = @Schema(implementation = ClientResponse.class))),
            @ApiResponse(responseCode = "404", description = "Client not found",
                    content = @Content(schema = @Schema(implementation = com.apishield.exception.ErrorResponse.class)))
    })
    public ResponseEntity<ClientResponse> getClientById(
            @Parameter(description = "Client UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }

    @PutMapping("/clients/{id}")
    @Operation(summary = "Update a client")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client updated",
                    content = @Content(schema = @Schema(implementation = ClientResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request",
                    content = @Content(schema = @Schema(implementation = com.apishield.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Client not found",
                    content = @Content(schema = @Schema(implementation = com.apishield.exception.ErrorResponse.class)))
    })
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request
    ) {
        return ResponseEntity.ok(clientService.updateClient(id, request));
    }

    @DeleteMapping("/clients/{id}")
    @Operation(summary = "Delete a client")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Client deleted"),
            @ApiResponse(responseCode = "404", description = "Client not found",
                    content = @Content(schema = @Schema(implementation = com.apishield.exception.ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }
}
