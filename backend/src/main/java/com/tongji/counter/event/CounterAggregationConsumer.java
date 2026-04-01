package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * 璁℃暟浜嬩欢鑱氬悎涓庡埛鍐欐秷璐硅€呫€?
 *
 * <p>鑱岃矗锛?/p>
 * - 娑堣垂鐐硅禐/鏀惰棌绛夊閲忎簨浠讹紝鍐欏叆 Redis 鑱氬悎妗讹紙Hash锛夛紱
 * - 浠ュ浐瀹氬欢杩熷畾鏃朵换鍔″皢鑱氬悎澧為噺鎶樺彔鍒?SDS 鍥哄畾缁撴瀯璁℃暟锛?
 * - 鍒峰啓鎴愬姛鍚庡垹闄よ仛鍚堝瓧娈碉紝閬垮厤閲嶅鍔犵畻銆?
 */
@Service
public class CounterAggregationConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrScript;
    private final DefaultRedisScript<Long> decrScript;

    // 浣跨敤 Redis Hash 浣滀负鎸佷箙鍖栬仛鍚堟《锛歛gg:{schema}:{etype}:{eid} 锛宖ield=idx 锛寁alue=delta
    public CounterAggregationConsumer(ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.incrScript = new DefaultRedisScript<>();
        this.incrScript.setResultType(Long.class);
        this.incrScript.setScriptText(INCR_FIELD_LUA); // 鍘熷瓙灏嗗閲忔姌鍙犲埌 SDS 鎸囧畾娈碉紙澶х 32 浣嶏級
        
        this.decrScript = new DefaultRedisScript<>();
        this.decrScript.setResultType(Long.class);
        this.decrScript.setScriptText(DECR_FIELD_LUA);
    }

    /**
     * 娑堣垂璁℃暟浜嬩欢骞跺啓鍏ヨ仛鍚堟《銆?
     * @param message 浜嬩欢 JSON
     * @param ack 浣嶇偣纭瀵硅薄锛堟墜鍔ㄦ彁浜わ級
     */
    @KafkaListener(topics = CounterTopics.EVENTS, groupId = "xingzhiquan-counter-agg")
    public void onMessage(String message, Acknowledgment ack) throws Exception {
        CounterEvent evt = objectMapper.readValue(message, CounterEvent.class);
        String aggKey = CounterKeys.aggKey(evt.getEntityType(), evt.getEntityId());
        String field = String.valueOf(evt.getIdx());
        try {
            // 灏嗗閲忔寔涔呭寲鍒?Redis Hash
            redis.opsForHash().increment(aggKey, field, evt.getDelta());
            // 鎴愬姛鍚庢彁浜や綅鐐癸紝缁戝畾鈥滃凡鎸佷箙鍖栤€濊涔?
            ack.acknowledge();
        } catch (Exception ex) {
            // 涓嶆彁浜や綅鐐逛互渚块噸璇?
        }
    }

    /**
     * 灏嗚仛鍚堝閲忓埛鍐欏埌 SDS 鍥哄畾缁撴瀯璁℃暟銆?
     * 鍥哄畾寤惰繜 1s锛屼繚璇佺绾ф渶缁堜竴鑷存€с€?
     */
    @Scheduled(fixedDelay = 1000L)
    public void flush() {
        // 绠€鍖栧疄鐜帮細鎵弿鎵€鏈夎仛鍚堟《閿紙鐢熶骇寤鸿浣跨敤绱㈠紩闆嗗悎鏇夸唬 KEYS锛?
        Set<String> keys = redis.keys("agg:" + CounterSchema.SCHEMA_ID + ":*");
        if (keys.isEmpty()) {
            return;
        }

        for (String aggKey : keys) {
            Map<Object, Object> entries = redis.opsForHash().entries(aggKey);
            if (entries.isEmpty()) {
                continue;
            }
            // 瑙ｆ瀽 etype/eid 浠ュ畾浣?SDS key
            String[] parts = aggKey.split(":", 4); // agg:schema:etype:eid
            if (parts.length < 4) {
                continue;
            }

            String cntKey = CounterKeys.sdsKey(parts[2], parts[3]);

            for (Map.Entry<Object, Object> e : entries.entrySet()) {
                String field = String.valueOf(e.getKey());
                // 澧為噺
                long delta;
                try {
                    delta = Long.parseLong(String.valueOf(e.getValue()));
                } catch (NumberFormatException nfe) {
                    continue;
                }
                if (delta == 0) continue;
                int idx;

                try {
                    idx = Integer.parseInt(field);
                } catch (NumberFormatException nfe) {
                    continue;
                }

                try {
                    redis.execute(incrScript, List.of(cntKey),
                            String.valueOf(CounterSchema.SCHEMA_LEN),
                            String.valueOf(CounterSchema.FIELD_SIZE),
                            String.valueOf(idx),
                            String.valueOf(delta));

                    // 鎴愬姛鍚庢墸鍑忚瀛楁锛岃嫢缁撴灉涓?鍒欏垹闄わ紝閬垮厤骞跺彂鍐欏叆涓㈠け
                    redis.execute(decrScript, List.of(aggKey), field, String.valueOf(delta));
                } catch (Exception ex) {
                    // 鐣欏瓨瀛楁锛屼笅涓€杞噸璇?
                }
            }
            // 濡?Hash 宸蹭负绌猴紝鍒犻櫎鑱氬悎妗禟ey
            // 鐩殑锛氶檷浣庨敭绌洪棿鍣煶锛岄伩鍏嶅悗缁棤鏁堟壂鎻?
            Long size = redis.opsForHash().size(aggKey);
            if (size == 0L) {
                redis.delete(aggKey);
            }
        }
    }

    private static final String INCR_FIELD_LUA = """
            
            local cntKey = KEYS[1]
            local schemaLen = tonumber(ARGV[1])
            local fieldSize = tonumber(ARGV[2]) -- 鍥哄畾涓?
            local idx = tonumber(ARGV[3])
            local delta = tonumber(ARGV[4])
            
            local function read32be(s, off)
              local b = {string.byte(s, off+1, off+4)}
              local n = 0
              for i=1,4 do n = n * 256 + b[i] end
              return n
            end
            
            local function write32be(n)
              local t = {}
              for i=4,1,-1 do t[i] = n % 256; n = math.floor(n/256) end
              return string.char(unpack(t))
            end
            
            local cnt = redis.call('GET', cntKey)
            if not cnt then cnt = string.rep(string.char(0), schemaLen * fieldSize) end
            local off = idx * fieldSize
            local v = read32be(cnt, off) + delta
            if v < 0 then v = 0 end
            local seg = write32be(v)
            cnt = string.sub(cnt, 1, off) .. seg .. string.sub(cnt, off+fieldSize+1)
            redis.call('SET', cntKey, cnt)
            return 1
            """;

    private static final String DECR_FIELD_LUA = """
            local key = KEYS[1]
            local field = ARGV[1]
            local delta = tonumber(ARGV[2])
            local v = redis.call('HINCRBY', key, field, -delta)
            if v == 0 then
                redis.call('HDEL', key, field)
            end
            return v
            """;
}