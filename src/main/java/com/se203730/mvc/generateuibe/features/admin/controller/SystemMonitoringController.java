package com.se203730.mvc.generateuibe.features.admin.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.admin.dto.response.SystemMonitoringResponse;
import com.se203730.mvc.generateuibe.features.admin.service.SystemMonitoringService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/system/monitoring")
public class SystemMonitoringController {
    private final SystemMonitoringService monitoring;

    public SystemMonitoringController(SystemMonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping
    public ResponseEntity<ResponseObject<SystemMonitoringResponse>> get(@CurrentUserUUID UUID adminId,
            @RequestParam(defaultValue = "24") int hours) {
        if (hours != 24 && hours != 168 && hours != 720) {
            return ResponseEntity.badRequest().build();
        }
        ResponseObject<SystemMonitoringResponse> response = new ResponseObject<>();
        response.setStatus(200);
        response.setMessage("Success");
        response.setData(monitoring.get(adminId, hours));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }
}
