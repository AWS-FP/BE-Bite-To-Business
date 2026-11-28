package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.features.admin.dto.response.ApiDiagnosticsResponse;
import com.se203730.mvc.generateuibe.features.admin.dto.response.ApiDiagnosticsResponse.Row;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.*;

@Service
public class ApiDiagnosticsService {
    private static final Logger LOG = LoggerFactory.getLogger(ApiDiagnosticsService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);
    private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+}");
    private final AccountAccessService accounts;
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private final AthenaClient athena;
    private final String output;
    private final String alb;
    private final String sql;
    private final Map<Integer, Job> jobs = new HashMap<>();

    private record Job(Instant to, String id, Instant retryAfter, ApiDiagnosticsResponse result) {}

    public ApiDiagnosticsService(AccountAccessService accounts,
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mappings,
            @Value("${app.monitoring.region:ap-southeast-1}") String region,
            @Value("${app.monitoring.profile:}") String awsProfile,
            @Value("${app.monitoring.athena-output:}") String output,
            @Value("${app.monitoring.alb:}") String alb) throws IOException {
        this.accounts = accounts;
        this.mappings = mappings;
        this.output = output;
        this.alb = alb;
        this.sql = new ClassPathResource("monitoring/api-diagnostics.sql").getContentAsString(StandardCharsets.UTF_8);
        this.athena = AthenaClient.builder().region(Region.of(region)).credentialsProvider(MonitoringCredentials.provider(awsProfile))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(6))).build();
    }

    public ApiDiagnosticsResponse get(UUID adminId, int hours) {
        accounts.requireAdmin(adminId);
        if (hours != 1 && hours != 6 && hours != 24 && hours != 168 && hours != 720) throw new IllegalArgumentException("Unsupported range");
        return report(hours);
    }

    private synchronized ApiDiagnosticsResponse report(int hours) {
        Instant now = Instant.now();
        Instant to = Instant.ofEpochSecond(now.getEpochSecond() / 900 * 900);
        Instant from = to.minus(Duration.ofHours(hours));
        if (output.isBlank() || alb.isBlank()) return empty(from, to, "UNCONFIGURED");
        Job job = jobs.get(hours);
        try {
            if (job == null || !job.to().equals(to) ||
                    (job.retryAfter() != null && !now.isBefore(job.retryAfter()))) {
                String query = sql.replace("{from}", from.toString()).replace("{to}", to.toString())
                        .replace("{firstDay}", DAY.format(from)).replace("{lastDay}", DAY.format(to.minusNanos(1)))
                        .replace("{alb}", alb.replace("'", "''")).replace("{routeCase}", routeCase());
                String id = athena.startQueryExecution(r -> r.queryString(query)
                        .resultConfiguration(c -> c.outputLocation(output))
                        .resultReuseConfiguration(c -> c.resultReuseByAgeConfiguration(a ->
                                a.enabled(true).maxAgeInMinutes(15)))).queryExecutionId();
                job = new Job(to, id, null, null);
                jobs.put(hours, job);
            }
            if (job.result() != null) return job.result();
            var execution = athena.getQueryExecution(GetQueryExecutionRequest.builder()
                    .queryExecutionId(job.id()).build()).queryExecution();
            switch (execution.status().state()) {
                case QUEUED, RUNNING -> { return empty(from, to, "LOADING"); }
                case SUCCEEDED -> {
                    var report = readResult(from, to, job.id());
                    jobs.put(hours, new Job(to, job.id(), null, report));
                    return report;
                }
                default -> LOG.warn("Admin API diagnostics query {} ended in {}", job.id(), execution.status().state());
            }
        } catch (SdkException ex) {
            LOG.warn("Unable to read admin API diagnostics", ex);
        }
        var unavailable = empty(from, to, "UNAVAILABLE");
        jobs.put(hours, new Job(to, null, now.plusSeconds(60), unavailable));
        return unavailable;
    }

    // Only registered route templates leave Athena. Query strings and customer identifiers stay out of the report.
    private String routeCase() {
        var routes = mappings.getObject().getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .filter(path -> path.startsWith("/api/")).distinct()
                .sorted(Comparator.<String>comparingLong(path -> path.chars().filter(c -> c == '{').count())
                        .thenComparing(Comparator.reverseOrder())).toList();
        StringBuilder result = new StringBuilder("CASE ");
        for (String route : routes) {
            String literal = route.replace("'", "''");
            if (route.contains("{")) {
                String pattern = Arrays.stream(route.split("/"))
                        .map(part -> VARIABLE.matcher(part).matches() ? "[^/]+" : Pattern.quote(part))
                        .collect(java.util.stream.Collectors.joining("/"));
                result.append("WHEN regexp_like(path, '^").append(pattern.replace("'", "''")).append("$')");
            } else {
                result.append("WHEN path = '").append(literal).append("'");
            }
            result.append(" THEN '").append(literal).append("' ");
        }
        return result.append("ELSE '/api/…' END").toString();
    }

    private ApiDiagnosticsResponse readResult(Instant from, Instant to, String id) {
        List<Row> errors = new ArrayList<>();
        List<Row> slowest = new ArrayList<>();
        var rows = athena.getQueryResults(r -> r.queryExecutionId(id).maxResults(100)).resultSet().rows();
        for (var data : rows.stream().skip(1).toList()) {
            var cells = data.data().stream().map(Datum::varCharValue).toList();
            Row row = new Row(cells.get(0), cells.get(1), cells.get(2) == null ? null : Integer.valueOf(cells.get(2)),
                    cells.get(3), Long.parseLong(cells.get(4)), number(cells.get(5)), number(cells.get(6)),
                    Instant.parse(cells.get(7)));
            (row.httpStatus() == null ? slowest : errors).add(row);
        }
        return new ApiDiagnosticsResponse(from, to, "AVAILABLE", errors, slowest);
    }

    private static Double number(String value) { return value == null ? null : Double.valueOf(value); }

    private static ApiDiagnosticsResponse empty(Instant from, Instant to, String status) {
        return new ApiDiagnosticsResponse(from, to, status, List.of(), List.of());
    }

    @PreDestroy
    void close() { athena.close(); }
}
