package com.se203730.mvc.generateuibe.features.admin.dto.response;

import java.time.Instant;
import java.util.List;

public record SystemMonitoringResponse(
        Instant from, Instant to, int periodSeconds, String cloudStatus,
        List<MetricSeries> metrics, List<AiPoint> ai, List<AiLog> recentAiLogs) {
    public record Point(Instant time, double value) {}
    public record MetricSeries(String id, String unit, List<Point> points) {}
    public record AiPoint(Instant time, long requests, long failed, long inputTokens, long outputTokens) {}
    public record AiLog(java.util.UUID id, Instant time, String model, String status, Long durationMs,
                        Long inputTokens, Long outputTokens, String errorCode) {}
}
