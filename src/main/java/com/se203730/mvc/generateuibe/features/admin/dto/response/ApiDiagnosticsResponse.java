package com.se203730.mvc.generateuibe.features.admin.dto.response;

import java.time.Instant;
import java.util.List;

public record ApiDiagnosticsResponse(Instant from, Instant to, String status,
        List<Row> errors, List<Row> slowest) {
    public record Row(String method, String route, Integer httpStatus, String source,
            long requests, Double averageMs, Double p95Ms, Instant lastSeen) {}
}
