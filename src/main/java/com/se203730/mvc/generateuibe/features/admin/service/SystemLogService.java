package com.se203730.mvc.generateuibe.features.admin.service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

/** Recent production application logs. CloudWatch Insights is asynchronous; never block a request waiting for it. */
@Service
public class SystemLogService {
    public record LogEntry(String time, String message) {}
    public record Result(Instant from, Instant to, String status, List<LogEntry> entries) {}
    private record Job(Instant from, Instant to, String queryId, Instant createdAt, Result result) {}
    private final AccountAccessService accounts;
    private final CloudWatchLogsClient client;
    private final String logGroup;
    private final Map<Integer, Job> jobs = new HashMap<>();

    public SystemLogService(AccountAccessService accounts,
            @Value("${app.monitoring.region:ap-southeast-1}") String region,
            @Value("${app.monitoring.profile:}") String awsProfile,
            @Value("${app.monitoring.log-group:}") String logGroup) {
        this.accounts = accounts;
        this.logGroup = logGroup;
        this.client = CloudWatchLogsClient.builder().region(Region.of(region)).credentialsProvider(MonitoringCredentials.provider(awsProfile))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(8))).build();
    }

    public synchronized Result get(UUID adminId, int hours) {
        accounts.requireAdmin(adminId);
        if (!Set.of(1, 6, 24, 168).contains(hours)) throw new IllegalArgumentException("Unsupported range");
        Instant now = Instant.now();
        Instant to = now;
        Instant from = to.minus(Duration.ofHours(hours));
        if (logGroup.isBlank()) return new Result(from, to, "UNCONFIGURED", List.of());
        try {
            Job job = jobs.get(hours);
            if (job == null || job.createdAt().plusSeconds(60).isBefore(now)) {
                String queryId = client.startQuery(StartQueryRequest.builder().logGroupName(logGroup)
                        .startTime(from.getEpochSecond()).endTime(to.getEpochSecond())
                        .queryString("fields @timestamp, @message | sort @timestamp desc | limit 100").build()).queryId();
                job = new Job(from, to, queryId, now, null);
                jobs.put(hours, job);
            }
            if (job.result() != null) return job.result();
            GetQueryResultsResponse response = client.getQueryResults(GetQueryResultsRequest.builder().queryId(job.queryId()).build());
            if (response.status() == QueryStatus.SCHEDULED || response.status() == QueryStatus.RUNNING)
                return new Result(job.from(), job.to(), "LOADING", List.of());
            if (response.status() != QueryStatus.COMPLETE) {
                jobs.remove(hours);
                return new Result(from, to, "UNAVAILABLE", List.of());
            }
            List<LogEntry> entries = new ArrayList<>();
            for (List<ResultField> row : response.results()) {
                String time = null, message = null;
                for (ResultField field : row) {
                    if ("@timestamp".equals(field.field())) time = field.value();
                    if ("@message".equals(field.field())) message = field.value();
                }
                if (time != null && message != null) entries.add(new LogEntry(time, sanitize(message)));
            }
            Result result = new Result(job.from(), job.to(), "AVAILABLE", List.copyOf(entries));
            jobs.put(hours, new Job(job.from(), job.to(), job.queryId(), job.createdAt(), result));
            return result;
        } catch (SdkException ex) {
            jobs.remove(hours);
            return new Result(from, to, "UNAVAILABLE", List.of());
        }
    }

    static String sanitize(String text) {
        String first = text.lines().findFirst().orElse("");
        first = first.replaceAll("(?i)(authorization|password|token|secret|api[_-]?key)\\s*[:=]\\s*\\S+", "$1=[redacted]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[email]")
                .replaceAll("(?i)bearer\\s+\\S+", "Bearer [redacted]");
        return first.length() > 500 ? first.substring(0, 500) + "…" : first;
    }

    @PreDestroy
    void close() { client.close(); }
}
