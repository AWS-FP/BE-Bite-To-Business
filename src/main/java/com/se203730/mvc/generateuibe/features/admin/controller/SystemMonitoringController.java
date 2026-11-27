package com.se203730.mvc.generateuibe.features.admin.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.admin.dto.response.SystemMonitoringResponse;
import com.se203730.mvc.generateuibe.features.admin.service.SystemMonitoringService;
import com.se203730.mvc.generateuibe.features.admin.service.ApiDiagnosticsService;
import com.se203730.mvc.generateuibe.features.admin.service.SystemLogService;
import com.se203730.mvc.generateuibe.features.admin.service.FrontendLogService;
import com.se203730.mvc.generateuibe.features.admin.dto.response.ApiDiagnosticsResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/system/monitoring")
public class SystemMonitoringController {
    private final SystemMonitoringService monitoring;

    private final ApiDiagnosticsService diagnostics;
    private final SystemLogService systemLogs;
    private final FrontendLogService frontendLogs;
    public SystemMonitoringController(SystemMonitoringService monitoring, ApiDiagnosticsService diagnostics, SystemLogService systemLogs, FrontendLogService frontendLogs) {
        this.monitoring = monitoring;
        this.diagnostics = diagnostics;
        this.systemLogs = systemLogs;
        this.frontendLogs = frontendLogs;
    }

    @GetMapping("/frontend-logs")
    public ResponseEntity<ResponseObject<FrontendLogService.Result>> frontendLogs(@CurrentUserUUID UUID adminId,
            @RequestParam(defaultValue="6") int hours, @RequestParam(required=false) Long before,
            @RequestParam(required=false) String nextToken) {
        if (!java.util.Set.of(1,6,24,168).contains(hours) || nextToken != null && nextToken.length()>4096) return ResponseEntity.badRequest().build();
        ResponseObject<FrontendLogService.Result> response=new ResponseObject<>();
        response.setStatus(200); response.setMessage("Success"); response.setData(frontendLogs.get(adminId,hours,before,nextToken));
        return ResponseEntity.ok().header("Cache-Control","no-store").body(response);
    }
    @GetMapping("/logs")
    public ResponseEntity<ResponseObject<SystemLogService.Result>> logs(@CurrentUserUUID UUID adminId,
            @RequestParam(defaultValue = "6") int hours) {
        if (hours != 1 && hours != 6 && hours != 24 && hours != 168) return ResponseEntity.badRequest().build();
        ResponseObject<SystemLogService.Result> response = new ResponseObject<>();
        response.setStatus(200);
        response.setMessage("Success");
        response.setData(systemLogs.get(adminId, hours));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }
    @GetMapping("/api-diagnostics")
    public ResponseEntity<ResponseObject<ApiDiagnosticsResponse>> diagnostics(@CurrentUserUUID UUID adminId,
            @RequestParam(defaultValue = "6") int hours) {
        if (hours != 1 && hours != 6 && hours != 24 && hours != 168 && hours != 720) return ResponseEntity.badRequest().build();
        ResponseObject<ApiDiagnosticsResponse> response = new ResponseObject<>();
        response.setStatus(200);
        response.setMessage("Success");
        response.setData(diagnostics.get(adminId, hours));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }

    @GetMapping
    public ResponseEntity<ResponseObject<SystemMonitoringResponse>> get(@CurrentUserUUID UUID adminId,
            @RequestParam(defaultValue = "6") int hours) {
        if (hours != 1 && hours != 6 && hours != 24 && hours != 168 && hours != 720) {
            return ResponseEntity.badRequest().build();
        }
        ResponseObject<SystemMonitoringResponse> response = new ResponseObject<>();
        response.setStatus(200);
        response.setMessage("Success");
        response.setData(monitoring.get(adminId, hours));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }
}
