package com.se203730.mvc.generateuibe.features.admin.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

class SystemLogServiceTest {
    @Test void requiresAdminBeforeCallingAws() {
        var accounts=mock(AccountAccessService.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
        var client=mock(CloudWatchLogsClient.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
        UUID id=UUID.randomUUID();
        doThrow(new IllegalStateException("denied")).when(accounts).requireAdmin(id);
        assertThrows(IllegalStateException.class,()->new SystemLogService(accounts,client,"/ecs/test").get(id,6));
        verifyNoInteractions(client);
    }
    @Test void returnsLoadingThenSanitizedResultsAndCachesTheQuery() {
        var accounts=mock(AccountAccessService.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
        var client=mock(CloudWatchLogsClient.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
        when(client.startQuery(any(StartQueryRequest.class))).thenReturn(StartQueryResponse.builder().queryId("query").build());
        when(client.getQueryResults(any(GetQueryResultsRequest.class)))
            .thenReturn(GetQueryResultsResponse.builder().status(QueryStatus.RUNNING).build())
            .thenReturn(GetQueryResultsResponse.builder().status(QueryStatus.COMPLETE).results(List.of(List.of(
                ResultField.builder().field("@timestamp").value("2026-11-28 00:00:00.000").build(),
                ResultField.builder().field("@message").value("WARN token=secret person@example.com\nprivate stack line").build()))).build());
        var service=new SystemLogService(accounts,client,"/ecs/test");
        UUID id=UUID.randomUUID();
        assertEquals("LOADING",service.get(id,6).status());
        var result=service.get(id,6);
        assertEquals("AVAILABLE",result.status());
        assertEquals(21600,result.to().getEpochSecond()-result.from().getEpochSecond());
        assertEquals("WARN token=[redacted] [email]",result.entries().getFirst().message());
        assertEquals(result,service.get(id,6));
        verify(client,times(1)).startQuery(any(StartQueryRequest.class));
    }
    @Test void reportsMissingConfigurationAndRejectsUnsupportedRanges() {
        var accounts=mock(AccountAccessService.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
        var client=mock(CloudWatchLogsClient.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
        var service=new SystemLogService(accounts,client,"");
        assertEquals("UNCONFIGURED",service.get(UUID.randomUUID(),1).status());
        assertThrows(IllegalArgumentException.class,()->service.get(UUID.randomUUID(),2));
        verifyNoInteractions(client);
    }
}
