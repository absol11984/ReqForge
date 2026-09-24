package com.apishield.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "APIShield",
                version = "0.1.0",
                description = "API rate limiting service — Phase 1 client management, Phase 2 Redis fixed-window rate limiting",
                contact = @Contact(name = "APIShield", url = "https://example.com", email = "no-reply@example.com"),
                license = @License(name = "MIT")
        ),
        servers = {
                @Server(url = "/")
        }
)
public class OpenApiConfig {
}
