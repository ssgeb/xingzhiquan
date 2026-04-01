package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 鐏鹃毦鍦烘櫙涓嬬殑璁℃暟閲嶅缓娑堣垂鑰咃細鍩轰簬 earliest 鍥炴斁鍘嗗彶浜嬩欢锛岀洿鎺ユ姌鍙犲埌 SDS銆?
 * 榛樿鍏抽棴锛屼粎褰?counter.rebuild.enabled=true 鏃跺惎鐢ㄣ€?
 */
@Service
@ConditionalOnProperty(name = "counter.rebuild.enabled", havingValue = "true")
public class CounterRebuildConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrScript;

    public CounterRebuildConsumer(ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.incrScript = new DefaultRedisScript<>();
        this.incrScript.setResultType(Long.class);
        this.incrScript.setScriptText(INCR_FIELD_LUA); // 澶嶇敤涓庤仛鍚堝埛鍐欎竴鑷寸殑鍘熷瓙鎶樺彔鑴氭湰
    }

    @KafkaListener(
            topics = CounterTopics.EVENTS,
            groupId = "xingzhiquan-counter-rebuild",
            properties = {"auto.offset.reset=earliest"}
    )
    public void onMessage(String message, Acknowledgment ack) throws Exception {
        // 鐏惧鍦烘櫙锛氫粠鏈€鏃╀綅鐐瑰洖鏀惧巻鍙蹭簨浠讹紝鐩存帴鎶樺彔鍒?SDS
        CounterEvent evt = objectMapper.readValue(message, CounterEvent.class);
        String cntKey = CounterKeys.sdsKey(evt.getEntityType(), evt.getEntityId());
        try {
            redis.execute(incrScript, List.of(cntKey),
                    String.valueOf(CounterSchema.SCHEMA_LEN),
                    String.valueOf(CounterSchema.FIELD_SIZE),
                    String.valueOf(evt.getIdx()),
                    String.valueOf(evt.getDelta()));
            ack.acknowledge(); // 鍐欏叆鎴愬姛鍚庢彁浜や綅鐐癸紝閬垮厤閲嶅鍥炴斁
        } catch (Exception ex) {
            // 涓嶆彁浜や綅鐐逛互渚块噸璇?
        }
    }

    // 澶嶇敤涓庤仛鍚堟秷璐硅€呬竴鑷寸殑鍘熷瓙璁℃暟鎶樺彔鑴氭湰
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
}