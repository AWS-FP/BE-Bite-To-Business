package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.features.admin.dto.response.SystemMonitoringResponse;
import com.se203730.mvc.generateuibe.features.admin.dto.response.SystemMonitoringResponse.*;
import com.se203730.mvc.generateuibe.features.ai.repository.AiModelRequestRepository;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

@Service
public class SystemMonitoringService {
    private final AccountAccessService accounts;
    private final AiModelRequestRepository requests;
    private final CloudWatchClient cloudwatch;
    private final List<MetricDataQuery> queries;
    private record Snapshot(Instant fetchedAt, SystemMonitoringResponse response) {}
    private final Map<Integer, Snapshot> cache = new HashMap<>();

    public SystemMonitoringService(AccountAccessService accounts, AiModelRequestRepository requests,
            @Value("${app.monitoring.region:ap-southeast-1}") String region,
            @Value("${app.monitoring.cluster:btb-cluster}") String cluster,
            @Value("${app.monitoring.service:btb-backend-service}") String service,
            @Value("${app.monitoring.rum:btb-frontend-performance}") String rum,
            @Value("${app.monitoring.alb:}") String alb) {
        this.accounts = accounts;
        this.requests = requests;
        this.cloudwatch = CloudWatchClient.builder().region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(4)).build()).build();
        queries = new ArrayList<>();
        queries.add(query("pageviews", "AWS/RUM", "PageViewCount", "Sum", Map.of("application_name", rum)));
        queries.add(query("sessions", "AWS/RUM", "SessionCount", "Sum", Map.of("application_name", rum)));
        queries.add(query("jserrors", "AWS/RUM", "JsErrorCount", "Sum", Map.of("application_name", rum)));
        Map<String, String> ecs = Map.of("ClusterName", cluster, "ServiceName", service);
        queries.add(query("cpu", "AWS/ECS", "CPUUtilization", "Average", ecs));
        queries.add(query("memory", "AWS/ECS", "MemoryUtilization", "Average", ecs));
        if (!alb.isBlank()) {
            queries.add(query("requests", "AWS/ApplicationELB", "RequestCount", "Sum", Map.of("LoadBalancer", alb)));
            queries.add(query("errors", "AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "Sum", Map.of("LoadBalancer", alb)));
            queries.add(query("latency", "AWS/ApplicationELB", "TargetResponseTime", "p95", Map.of("LoadBalancer", alb)));
        }
    }

    private MetricDataQuery query(String id, String namespace, String name, String stat, Map<String, String> dimensions) {
        Metric metric = Metric.builder().namespace(namespace).metricName(name).dimensions(dimensions.entrySet().stream()
                .map(entry -> Dimension.builder().name(entry.getKey()).value(entry.getValue()).build()).toList()).build();
        return MetricDataQuery.builder().id(id).returnData(true)
                .metricStat(MetricStat.builder().metric(metric).stat(stat).period(300).build()).build();
    }

    public synchronized SystemMonitoringResponse get(UUID adminId, int hours) {
        accounts.requireAdmin(adminId);
        if (hours != 24 && hours != 168 && hours != 720) {
            throw new IllegalArgumentException("Unsupported monitoring range");
        }
        Instant now = Instant.now();
        Snapshot cached = cache.get(hours);
        if (cached != null && cached.fetchedAt().plusSeconds(60).isAfter(now)) {
            return cached.response();
        }
        int period = hours == 24 ? 300 : hours == 168 ? 3600 : 86400;
        // Align both windows to the same buckets so partial periods are not compared.
        Instant to = Instant.ofEpochSecond(now.getEpochSecond() / period * period);
        Instant from = to.minus(Duration.ofHours(hours));
        Instant previousFrom = from.minus(Duration.ofHours(hours));
        List<MetricSeries> metrics = new ArrayList<>();
        String cloudStatus = "AVAILABLE";
        try {
            Map<String, List<Point>> points = new HashMap<>();
            String next = null;
            do {
                GetMetricDataResponse result = cloudwatch.getMetricData(GetMetricDataRequest.builder()
                        .startTime(previousFrom).endTime(to).scanBy(ScanBy.TIMESTAMP_ASCENDING).nextToken(next)
                        .metricDataQueries(queries.stream().map(query -> query.toBuilder().metricStat(
                                query.metricStat().toBuilder().period(period).build()).build()).toList()).build());
                for (MetricDataResult item : result.metricDataResults()) {
                    if (item.statusCode() != StatusCode.COMPLETE) cloudStatus = "PARTIAL";
                    List<Point> values = points.computeIfAbsent(item.id(), key -> new ArrayList<>());
                    for (int i = 0; i < item.timestamps().size(); i++) {
                        values.add(new Point(item.timestamps().get(i), item.values().get(i)));
                    }
                }
                next = result.nextToken();
            } while (next != null && !next.isBlank());
            points.forEach((id, values) -> metrics.add(new MetricSeries(id,
                    id.equals("latency") ? "seconds" : id.equals("cpu") || id.equals("memory") ? "percent" : "count",
                    values.stream().sorted(java.util.Comparator.comparing(Point::time)).toList())));
        } catch (SdkException exception) {
            // AWS failures must not hide the independent database usage figures.
            cloudStatus = "UNAVAILABLE";
        }
        List<AiPoint> ai = requests.summarize(previousFrom, to, period).stream().map(row -> new AiPoint(
                Instant.ofEpochSecond(((Number) row[0]).longValue()), ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue(), ((Number) row[3]).longValue(), ((Number) row[4]).longValue())).toList();
        List<AiLog> logs = requests.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                from, to, PageRequest.of(0, 30)).stream().map(row -> new AiLog(row.getCreatedAt(), row.getModelId(),
                        row.getStatus(), row.getDurationMs(), row.getInputTokens(), row.getOutputTokens(), row.getErrorCode())).toList();
        SystemMonitoringResponse response = new SystemMonitoringResponse(from, to, period, cloudStatus, metrics, ai, logs);
        cache.put(hours, new Snapshot(now, response));
        return response;
    }

    @PreDestroy
    void close() {
        cloudwatch.close();
    }
}
