package com.se203730.mvc.generateuibe.common.outbox.client;

/** Nơi nhận event từ outbox (SQS hoặc EventBridge). */
public interface OutboxBrokerClient {
    /** Giá trị cột destination mà client này xử lý: SQS hoặc EVENTBRIDGE. */
    String destination();

    /**
     * Đẩy một event đã đóng gói JSON. Trả về message id của broker; ném lỗi nếu broker từ chối.
     */
    String publish(OutboxMessage message);

    record OutboxMessage(String eventId, String eventType, String aggregateId, String body) {
    }
}
