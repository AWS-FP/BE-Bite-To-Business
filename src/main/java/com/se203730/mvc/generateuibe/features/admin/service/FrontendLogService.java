package com.se203730.mvc.generateuibe.features.admin.service;

import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rum.RumClient;
import software.amazon.awssdk.services.rum.model.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class FrontendLogService {
    public record Entry(String time,String type,String page,String browser,String message,Integer httpStatus,Double durationMs) {}
    public record Result(Instant from,Instant to,String status,List<Entry> entries,String nextToken) {}
    private final AccountAccessService accounts;
    private final RumClient rum;
    private final ObjectMapper mapper;
    private final String monitor;
    public FrontendLogService(AccountAccessService accounts,ObjectMapper mapper,
            @Value("${app.monitoring.region:ap-southeast-1}") String region,
            @Value("${app.monitoring.profile:}") String profile,
            @Value("${app.monitoring.rum:btb-frontend-performance}") String monitor) {
        this.accounts=accounts;this.mapper=mapper;this.monitor=monitor;
        rum=RumClient.builder().region(Region.of(region)).credentialsProvider(MonitoringCredentials.provider(profile))
                .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(10))).build();
    }
    public Result get(UUID adminId,int hours,Long before,String token) {
        accounts.requireAdmin(adminId);
        if(!Set.of(1,6,24,168).contains(hours))throw new IllegalArgumentException("Unsupported range");
        Instant now=Instant.now(),to=before==null?now:Instant.ofEpochMilli(before);
        if(to.isAfter(now.plusSeconds(5))||to.isBefore(now.minus(Duration.ofDays(8))))throw new IllegalArgumentException("Invalid range");
        Instant from=to.minus(Duration.ofHours(hours));
        try {
            var response=rum.getAppMonitorData(GetAppMonitorDataRequest.builder().name(monitor).maxResults(100)
                    .timeRange(TimeRange.builder().after(from.toEpochMilli()).before(to.toEpochMilli()).build()).nextToken(token).build());
            List<Entry> entries=new ArrayList<>();
            for(String raw:response.events()) {
                JsonNode e=mapper.readTree(raw),d=object(e.path("event_details")),m=object(e.path("metadata"));
                String message=d.path("message").asString(d.path("error").path("message").asString(""));
                entries.add(new Entry(e.path("event_timestamp").asString(""),e.path("event_type").asString("unknown"),
                        m.path("pageId").asString("").split("[?#]",2)[0],m.path("browserName").asString(""),
                        SystemLogService.sanitize(message),d.path("response").path("status").isNumber()?d.path("response").path("status").asInt():null,
                        d.path("duration").isNumber()?d.path("duration").asDouble():null));
            }
            return new Result(from,to,"AVAILABLE",entries,response.nextToken());
        } catch(SdkException ex){return new Result(from,to,"UNAVAILABLE",List.of(),null);}
    }
    private JsonNode object(JsonNode node){return node.isString()?mapper.readTree(node.asString()):node;}
    @PreDestroy void close(){rum.close();}
}
