package com.tongji.search.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.relation.outbox.OutboxTopics;
import com.tongji.common.util.OutboxMessageUtil;
import com.tongji.search.index.SearchIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 鎼滅储绱㈠紩鐨?Outbox 娑堣垂鑰咃細鐩戝惉 xingzhiquan-canal-outbox锛岄┍鍔?ES 绱㈠紩鐨勫閲忔洿鏂般€?
 * 浠呭鐞?entity=knowpost 鐨?upsert 涓庤蒋鍒犮€?
 */
@Service
@RequiredArgsConstructor
public class CanalOutboxConsumerSearch {
    private final ObjectMapper objectMapper;
    private final SearchIndexService indexService;

    /**
     * 娑堣垂 outbox 娑堟伅锛岃В鏋愬悎娉曡骞舵寜瀹炰綋绫诲瀷鏇存柊绱㈠紩銆?
     */
    @KafkaListener(topics = OutboxTopics.CANAL_OUTBOX, groupId = "xingzhiquan-search-index-consumer")
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

                JsonNode payload = objectMapper.readTree(payloadNode.asText());
                String entity = text(payload.get("entity"));
                String op = text(payload.get("op"));
                Long id = asLong(payload.get("id"));
                if (!"knowpost".equals(entity) || id == null) {
                    continue;
                }

                // 杞垹涓?upsert锛屽潎瑕嗙洊鍐欏叆鍚屼竴鏂囨。 ID锛屼繚璇佸箓绛?
                if ("delete".equalsIgnoreCase(op)) {
                    indexService.softDeleteKnowPost(id);
                } else {
                    indexService.upsertKnowPost(id);
                }
            }
            // 鎻愪氦浣嶇偣锛岀‘淇濃€滃凡澶勭悊鈥濈殑璇箟
            ack.acknowledge();
        } catch (Exception ignored) {}
    }

    private String text(JsonNode n) {
        return n == null ? null : n.asText();
    }

    private Long asLong(JsonNode n) {
        if (n == null) {
            return null;
        }

        try {
            return Long.parseLong(n.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
