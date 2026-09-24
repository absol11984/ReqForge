package com.apishield.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@Tag(name = "Demo", description = "Rate-limited demo endpoints")
public class DemoController {

    @GetMapping("/products")
    @Operation(summary = "List demo products (rate limited)",
               description = "Returns a small product catalogue. Requires a valid X-API-Key header.")
    public ResponseEntity<List<Map<String, Object>>> getProducts() {
        List<Map<String, Object>> products = List.of(
                Map.of("id", 1, "name", "Widget Pro",     "price", 29.99),
                Map.of("id", 2, "name", "Gadget Plus",    "price", 49.99),
                Map.of("id", 3, "name", "Doohickey Max",  "price", 9.99)
        );
        return ResponseEntity.ok(products);
    }
}
