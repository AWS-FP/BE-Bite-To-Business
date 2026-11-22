package com.se203730.mvc.generateuibe.features.ai.client;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;

@Component
public class BedrockClient {
    private final BedrockRuntimeClient client;
    private final StsClient sts;
    private final String modelId;

    /**
     * Bedrock có thể nằm ở tài khoản AWS khác. Khi app.bedrock.assume-role-arn có giá trị, client assume role
     * đó bằng STS (tự gia hạn trước khi hết hạn); để trống thì dùng credentials mặc định (task role / profile local).
     */
    public BedrockClient(
            @Value("${app.bedrock.region}") String region,
            @Value("${app.bedrock.model-id}") String modelId,
            @Value("${app.bedrock.assume-role-arn:}") String assumeRoleArn,
            @Value("${app.bedrock.external-id:}") String externalId) {
        this.modelId = modelId;
        BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(45))
                        .apiCallAttemptTimeout(Duration.ofSeconds(40))
                        .build());
        if (assumeRoleArn == null || assumeRoleArn.isBlank()) {
            this.sts = null;
        } else {
            this.sts = StsClient.builder().region(Region.of(region)).build();
            builder.credentialsProvider(StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(sts)
                    .refreshRequest(request -> {
                        request.roleArn(assumeRoleArn.trim())
                                .roleSessionName("btb-backend-bedrock")
                                .durationSeconds(3600);
                        if (externalId != null && !externalId.isBlank()) {
                            request.externalId(externalId.trim());
                        }
                    })
                    .build());
        }
        this.client = builder.build();
    }

    public String reply(String prompt) {
        String instructions = "Bạn là trợ lý của Bite to Business. Trả lời ngắn gọn, rõ ràng bằng tiếng Việt. "
                + "Bạn chưa được cung cấp dữ liệu sản phẩm, giá, tồn kho, đơn hàng hay chính sách thực tế. "
                + "Không tự bịa các thông tin đó; hãy nói chưa có dữ liệu và hỏi thêm nếu cần.";
        return complete(instructions, prompt, 512, 0.4F);
    }

    /** Gọi model một lượt với system prompt riêng; trả toàn bộ text model sinh ra. */
    public String complete(String instructions, String prompt, int maxTokens, float temperature) {
        return converse(instructions, prompt, maxTokens, temperature).text();
    }

    public String modelId() {
        return modelId;
    }

    /** Kết quả một lượt gọi model, kèm số token và thời gian để ghi ai_model_requests. */
    public record ModelReply(String text, Integer inputTokens, Integer outputTokens, long durationMs) {
    }

    public ModelReply converse(String instructions, String prompt, int maxTokens, float temperature) {
        long started = System.nanoTime();
        Message message = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(prompt))
                .build();
        ConverseResponse response = client.converse(request -> request
                .modelId(modelId)
                .system(SystemContentBlock.fromText(instructions))
                .messages(message)
                .inferenceConfig(config -> config.maxTokens(maxTokens).temperature(temperature)));

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
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        Integer input = response.usage() == null ? null : response.usage().inputTokens();
        Integer output = response.usage() == null ? null : response.usage().outputTokens();
        return new ModelReply(text.toString(), input, output, durationMs);
    }

    @PreDestroy
    public void close() {
        client.close();
        if (sts != null) {
            sts.close();
        }
    }
}
