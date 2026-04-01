package com.tongji.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.CompletionProperty;
import co.elastic.clients.elasticsearch._types.mapping.DateProperty;
import co.elastic.clients.elasticsearch._types.mapping.IntegerNumberProperty;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.LongNumberProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

/**
 * 鎼滅储绱㈠紩鍒濆鍖栵細搴旂敤鍚姩鏃剁‘淇濈储寮曚笌 Mapping 瀛樺湪銆?
 * 娉ㄦ剰锛歵itle/body 浣跨敤 IK 鍒嗚瘝鍣紝闇€鍦?ES 闆嗙兢瀹夎 analysis-ik 鎻掍欢銆?
 */
@Service
@RequiredArgsConstructor
public class SearchIndexInitializer {
    private final ElasticsearchClient es;
    private static final String INDEX = "xingzhiquan_content_index";

    @PostConstruct
    public void ensureIndex() {
        try {
            boolean exists = es.indices().exists(e -> e.index(INDEX)).value();
            if (exists) {
                return;
            }

            es.indices().create(c -> c.index(INDEX).mappings(m -> m
                    .properties("content_id", Property.of(p -> p.long_(LongNumberProperty.of(b -> b))))
                    .properties("content_type", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("description", Property.of(p -> p.text(TextProperty.of(b -> b.analyzer("ik_max_word")))))
                    // IK 鍒嗚瘝锛歵itle 浣跨敤 ik_max_word锛屾绱娇鐢?ik_smart锛沚ody 浣跨敤 ik_max_word
                    .properties("title", Property.of(p -> p.text(TextProperty.of(b -> b.analyzer("ik_max_word").searchAnalyzer("ik_smart")))))
                    .properties("body", Property.of(p -> p.text(TextProperty.of(b -> b.analyzer("ik_max_word")))))
                    .properties("tags", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("author_id", Property.of(p -> p.long_(LongNumberProperty.of(b -> b))))
                    .properties("author_avatar", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("author_nickname", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("author_tag_json", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("publish_time", Property.of(p -> p.date(DateProperty.of(b -> b))))
                    .properties("like_count", Property.of(p -> p.integer(IntegerNumberProperty.of(b -> b))))
                    .properties("favorite_count", Property.of(p -> p.integer(IntegerNumberProperty.of(b -> b))))
                    .properties("view_count", Property.of(p -> p.integer(IntegerNumberProperty.of(b -> b))))
                    .properties("status", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("img_urls", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("is_top", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("title_suggest", Property.of(p -> p.completion(CompletionProperty.of(b -> b)))
                    )));
        } catch (Exception ignored) {
            // 蹇界暐寮傚父浠ヤ繚璇佸簲鐢ㄥ惎鍔紱绱㈠紩鍙兘鐢卞悗缁啓鍏ュ姩鎬佸垱寤猴紝浣?Mapping 灏嗕笉瀹屾暣
        }
    }
}