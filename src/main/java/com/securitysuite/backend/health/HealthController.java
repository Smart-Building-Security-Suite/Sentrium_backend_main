package com.securitysuite.backend.health;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    @Operation(summary = "Health check endpoint",
               description = "Returns the health status of the application and its dependencies including database connectivity. Used by monitoring systems and load balancers. Publicly accessible.")
    public ResponseEntity<Map<String, Object>> health() {
        String dbStatus = "UP";
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(1)) dbStatus = "DOWN";
        } catch (Exception e) {
            dbStatus = "DOWN";
        }
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("status", "UP");
        response.put("db", dbStatus);
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }
}
