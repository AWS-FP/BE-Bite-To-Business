package com.se203730.mvc.generateuibe.common.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API công khai để kiểm tra backend đang chạy; không kiểm tra kết nối database hoặc dịch vụ bên ngoài.
 */
@RestController
@Tag(name = "Health", description = "Backend availability")
public class HealthController {

    @GetMapping("/api/health")
    @SecurityRequirements
    @Operation(summary = "Check that the backend is running",
            description = "Public liveness check. Does not check database or external services.")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "Bite to Business API");
    }
}
