package com.tongji.relation.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.processor.RelationEventProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;
import com.tongji.common.util.OutboxMessageUtil;

/**
 * Canal Outbox 娑堣垂鑰呫€?
 * 鑱岃矗锛氭秷璐?Canal 妗ユ帴鍐欏叆鐨?outbox 涓婚娑堟伅锛屾彁鍙?payload 骞跺弽搴忓垪鍖栦负 RelationEvent锛屼氦鐢卞鐞嗗櫒钀藉簱涓庢洿鏂扮紦瀛?璁℃暟锛涗娇鐢ㄦ墜鍔ㄤ綅鐐圭‘淇濆鐞嗘垚鍔熻涔夈€?
 */
@Service
public class CanalOutboxConsumer {
    private final ObjectMapper objectMapper;
    private final RelationEventProcessor processor;

    /**
     * Outbox 娑堣垂鑰呮瀯閫犲嚱鏁般€?
     * @param objectMapper JSON 搴忓垪鍖栧櫒
     * @param processor 鍏崇郴浜嬩欢澶勭悊鍣?
     */
    public CanalOutboxConsumer(ObjectMapper objectMapper, RelationEventProcessor processor) {
        this.objectMapper = objectMapper;
        this.processor = processor;
    }

    /**
     * 娑堣垂 Canal outbox 娑堟伅骞惰浆涓哄叧绯讳簨浠跺鐞嗐€?
     * 鐩戝惉 Canal鈫扠afka 妗ユ帴鍐欏叆鐨?outbox 涓婚锛涗娇鐢ㄦ墜鍔ㄤ綅鐐规彁浜?
     * @param message Kafka 娑堟伅鍐呭
     * @param ack 浣嶇偣纭瀵硅薄
     */
    @KafkaListener(topics = OutboxTopics.CANAL_OUTBOX, groupId = "xingzhiquan-relation-outbox-consumer")
    public void onMessage(String message, Acknowledgment ack) {
        try {
            List<JsonNode> rows = OutboxMessageUtil.extractRows(objectMapper, message);
            if (rows.isEmpty()) {
                ack.acknowledge();
                return;
            }
            for (JsonNode row : rows) {
                JsonNode payloadNode = row.get("payload");
                if (payloadNode == null) {
                    continue;
                }
                
                RelationEvent evt = objectMapper.readValue(payloadNode.asText(), RelationEvent.class);
                processor.process(evt);
            }
            ack.acknowledge();
        } catch (Exception ignored) {}
    }
}
