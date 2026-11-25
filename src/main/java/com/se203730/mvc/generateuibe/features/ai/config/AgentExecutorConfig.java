package com.se203730.mvc.generateuibe.features.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread pool riêng cho pipeline AI: mỗi request SSE chạy nền tối đa ~3 lượt gọi Bedrock (40 giây mỗi lượt),
 * nên tách khỏi thread của Tomcat. Hàng đợi nhỏ (10) để khi quá tải thì từ chối sớm (429) thay vì treo request.
 */
@Configuration
public class AgentExecutorConfig {
    @Bean("agentExecutor")
    public ThreadPoolTaskExecutor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("bedrock-agent-");
        return executor;
    }
}
