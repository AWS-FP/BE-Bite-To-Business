package com.se203730.mvc.generateuibe.features.ai.client;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

@Component
public class BedrockClient {
    private final BedrockRuntimeClient client;
    private final String modelId;

    public BedrockClient(
            @Value("${app.bedrock.region}") String region,
            @Value("${app.bedrock.model-id}") String modelId) {
        this.modelId = modelId;
        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(45))
                        .apiCallAttemptTimeout(Duration.ofSeconds(40))
                        .build())
                .build();
    }

    public String reply(String prompt) {
        String instructions = "Bạn là trợ lý của Bite to Business. Trả lời ngắn gọn, rõ ràng bằng tiếng Việt. "
                + "Bạn chưa được cung cấp dữ liệu sản phẩm, giá, tồn kho, đơn hàng hay chính sách thực tế. "
                + "Không tự bịa các thông tin đó; hãy nói chưa có dữ liệu và hỏi thêm nếu cần.";
        Message message = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(prompt))
                .build();
        ConverseResponse response = client.converse(request -> request
                .modelId(modelId)
                .system(SystemContentBlock.fromText(instructions))
                .messages(message)
                .inferenceConfig(config -> config.maxTokens(512).temperature(0.4F)));

        if (response.output() == null || response.output().message() == null) {
            throw new IllegalStateException("Bedrock returned no message");
        }
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.output().message().content()) {
            if (block.text() != null) {
                text.append(block.text());
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("Bedrock returned no text");
        }
        return text.toString();
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}
