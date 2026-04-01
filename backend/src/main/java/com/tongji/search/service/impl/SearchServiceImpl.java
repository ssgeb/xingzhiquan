package com.tongji.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.util.NamedValue;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.tongji.knowpost.api.dto.FeedItemResponse;
import com.tongji.counter.service.CounterService;
import com.tongji.search.api.dto.SearchResponse;
import com.tongji.search.api.dto.SuggestResponse;
import com.tongji.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 鎼滅储鏈嶅姟瀹炵幇锛?
 * - 瀹藉彫鍥烇紙multi_match 鍛戒腑 title^3 涓?body锛? 涓氬姟鍔犳潈锛坒unction_score锛?
 * - 杩囨护锛坰tatus=published銆佸彲閫?tags锛? 鎺掑簭锛坰core/publish_time/like/view/content_id锛?
 * - 楂樹寒鐗囨鍚堝苟涓?snippet锛涙父鏍囧垎椤典娇鐢?search_after
 */
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchClient es;
    private final CounterService counterService;
    /**
     * ES 绱㈠紩鍚嶏細zhiguang 鍐呭缁熶竴绱㈠紩銆?
     */
    private static final String INDEX = "xingzhiquan_content_index";

    /**
     * 鍏抽敭璇嶆绱細鐩稿叧鎬?+ 浜掑姩鏁版嵁鍔犳潈锛屾敮鎸佹父鏍囧垎椤典笌楂樹寒銆?
     */
    @SuppressWarnings("unchecked")
    public SearchResponse search(String q, int size, String tagsCsv, String after, Long currentUserIdNullable) {
        List<String> tags = parseCsv(tagsCsv);
        List<FieldValue> afterValues = parseAfter(after);

        // 澶嶅悎鎺掑簭锛氫紭鍏堢浉鍏虫€э紝鍏舵鍙戝竷鏃堕棿涓庝簰鍔ㄦ暟鎹紝鏈€鍚庢寜 content_id 绋冲畾鎺掑簭
        List<SortOptions> sorts = new ArrayList<>();
        sorts.add(SortOptions.of(s -> s.score(o -> o.order(SortOrder.Desc))));
        sorts.add(SortOptions.of(s -> s.field(f -> f.field("publish_time").order(SortOrder.Desc))));
        sorts.add(SortOptions.of(s -> s.field(f -> f.field("like_count").order(SortOrder.Desc))));
        sorts.add(SortOptions.of(s -> s.field(f -> f.field("view_count").order(SortOrder.Desc))));
        sorts.add(SortOptions.of(s -> s.field(f -> f.field("content_id").order(SortOrder.Desc))));

        // 瀹屾暣鍖呭悕锛屼笉鐒跺拰鑷畾涔夌殑 SearchResponse 鍐茬獊
        co.elastic.clients.elasticsearch.core.SearchResponse<Map<String, Object>> resp;
        try {
            resp = es.search(s -> {
                var b = s.index(INDEX)
                        .size(size)
                        // 鍙洖涓庡姞鏉冿細鍏堟瀯閫?bool 鏌ヨ锛屽啀鐢?function_score 鍋氫簰鍔ㄦ暟鎹姞鏉?
                        .query(qb -> qb.functionScore(fs -> fs
                                .query(qb2 -> qb2.bool(bq -> {
                                    bq.must(m -> m.multiMatch(mm -> mm.query(q)
                                            .fields("title^3", "body")));
                                    bq.filter(f -> f.term(t -> t.field("status")
                                            .value(v -> v.stringValue("published"))));

                                    if (tags != null && !tags.isEmpty()) {
                                        bq.filter(f -> f.terms(t -> t.field("tags")
                                                .terms(tv -> tv.value(tags.stream().map(FieldValue::of).toList()))));
                                    }
                                    return bq;
                                }))
                                // 瀵圭偣璧炴暟鏀惰棌鏁拌缃潈閲?
                                .functions(fn -> fn.fieldValueFactor(fvf -> fvf.field("like_count")
                                        .modifier(FieldValueFactorModifier.Log1p))
                                        .weight(2.0))
                                .functions(fn -> fn.fieldValueFactor(fvf -> fvf.field("view_count")
                                        .modifier(FieldValueFactorModifier.Log1p))
                                        .weight(1.0))
                                .boostMode(FunctionBoostMode.Sum)
                        ))
                        // 杩斿洖 title/body 楂樹寒鐗囨锛屽悗缁悎骞朵负 snippet
                        .highlight(h -> h
                                .fields(new NamedValue<>("title", new HighlightField.Builder().build()))
                                .fields(new NamedValue<>("body", new HighlightField.Builder().build()))
                        )
                        .sort(sorts);
                // 娓告爣鍒嗛〉锛氭惡甯︿笂涓€娆℃渶鍚庡懡涓殑 sort 鍊?
                if (afterValues != null && !afterValues.isEmpty()) {
                    b = b.searchAfter(afterValues);
                }

                return b;
            }, (Class<Map<String, Object>>)(Class<?>) Map.class);
        } catch (Exception e) {
            return new SearchResponse(Collections.emptyList(), null, false);
        }

        List<FeedItemResponse> items = new ArrayList<>();
        List<Hit<Map<String, Object>>> hits = resp.hits() == null ? Collections.emptyList() : resp.hits().hits();

        for (Hit<Map<String, Object>> hit : hits) {
            Map<String, Object> source = hit.source();
            if (source == null) {
                continue;
            }
            String id = asString(source.get("content_id"));
            String title = asString(source.get("title"));
            String descriptionFromDoc = asString(source.get("description"));
            String snippet = buildSnippet(hit);
            String description = (snippet != null && !snippet.isBlank()) ? snippet : descriptionFromDoc;
            List<String> tagList = asStringList(source.get("tags"));
            List<String> imgs = asStringList(source.get("img_urls"));
            String cover = imgs.isEmpty() ? null : imgs.get(0);
            String authorAvatar = asString(source.get("author_avatar"));
            String authorNickname = asString(source.get("author_nickname"));
            String tagJson = asString(source.get("author_tag_json"));
            Long likeCount = asLong(source.get("like_count"));
            Long favoriteCount = asLong(source.get("favorite_count"));
            Boolean liked = currentUserIdNullable != null && counterService.isLiked("knowpost", id, currentUserIdNullable);
            Boolean faved = currentUserIdNullable != null && counterService.isFaved("knowpost", id, currentUserIdNullable);
            items.add(new FeedItemResponse(
                    id,
                    title,
                    description,
                    cover,
                    tagList,
                    authorAvatar,
                    authorNickname,
                    tagJson,
                    likeCount,
                    favoriteCount,
                    liked,
                    faved,
                    null
            ));
        }

        String nextAfter = null;
        boolean hasMore = items.size() >= size;

        if (!hits.isEmpty()) {
            List<FieldValue> sv = hits.get(hits.size() - 1).sort();
            if (sv != null && !sv.isEmpty()) {
                List<String> parts = sv.stream().map(this::fieldValueToString).collect(Collectors.toList());
                nextAfter = Base64.getUrlEncoder().withoutPadding().encodeToString(String.join(",", parts).getBytes());
            }
        }

        return new SearchResponse(items, nextAfter, hasMore);
    }

    /**
     * 鑱旀兂寤鸿锛欳ompletion Suggester锛屽彇 title_suggest 鐨勫€欓€夋枃鏈€?
     */
    @SuppressWarnings("unchecked")
    public SuggestResponse suggest(String prefix, int size) {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map<String, Object>> resp;
        try {
            resp = es.search(s -> s.index(INDEX)
                    .suggest(sug -> sug.suggesters("title_suggest",
                            sc -> sc.prefix(prefix).completion(c -> c.field("title_suggest").size(size))))
                    , (Class<Map<String, Object>>)(Class<?>) Map.class);
        } catch (Exception e) {
            return new SuggestResponse(Collections.emptyList());
        }
        List<String> items = new ArrayList<>();
        try {
            var sugg = resp.suggest();
            List<Suggestion<Map<String, Object>>> entry = sugg == null ? null : sugg.get("title_suggest");
            if (entry != null) {
                for (var s : entry) {
                    var comp = s.completion();
                    if (comp != null && comp.options() != null) {
                        for (var opt : comp.options()) {
                            String text = opt.text();
                            if (text != null && !text.isBlank()) {
                                items.add(text);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return new SuggestResponse(items);
    }

    /**
     * 閫楀彿鍒嗛殧瀛楃涓茶В鏋愪负鍒楄〃锛涚┖瀛楃涓茶繑鍥?null銆?
     */
    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }

        String[] parts = csv.split(",");
        List<String> out = new ArrayList<>();

        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }

        }
        return out;
    }

    /**
     * 瑙ｆ瀽 Base64URL 娓告爣涓?sort 鍊兼暟缁勶紝鎸夐『搴忚繕鍘熷悇 FieldValue銆?
     */
    private List<FieldValue> parseAfter(String after) {
        if (after == null || after.isBlank()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(after));
            String[] parts = decoded.split(",");
            List<FieldValue> out = new ArrayList<>(parts.length);

            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                if (i == 0) {
                    out.add(FieldValue.of(Double.parseDouble(p)));
                } else if (i == 1) {
                    out.add(FieldValue.of(Long.parseLong(p)));
                } else {
                    out.add(FieldValue.of(Long.parseLong(p)));
                }
            }

            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 鍚堝苟楂樹寒鐗囨涓?snippet锛堟爣棰樼墖娈靛湪鍓嶏紝姝ｆ枃鐗囨鍦ㄥ悗锛夈€?
     */
    private String buildSnippet(Hit<Map<String, Object>> hit) {
        StringBuilder sb = new StringBuilder();

        if (hit.highlight() != null) {
            List<String> ht = hit.highlight().get("title");
            if (ht != null && !ht.isEmpty()) {
                sb.append(String.join(" ", ht));
            }

            List<String> hb = hit.highlight().get("body");
            if (hb != null && !hb.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(" ");
                }
                sb.append(String.join(" ", hb));
            }
        }

        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * 灏?sort 鐨?FieldValue 瀹夊叏杞崲涓哄瓧绗︿覆锛屼究浜庣紪鐮佹父鏍囥€?
     */
    private String fieldValueToString(FieldValue fv) {
        if (fv.isDouble()) {
            return String.valueOf(fv.doubleValue());
        }
        if (fv.isLong()) {
            return String.valueOf(fv.longValue());
        }
        if (fv.isString()) {
            return fv.stringValue();
        }
        if (fv.isBoolean()) {
            return String.valueOf(fv.booleanValue());
        }

        return String.valueOf(fv._get());
    }

    /**
     * 浠绘剰瀵硅薄杞瓧绗︿覆锛宯ull 淇濇姢銆?
     */
    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 浠绘剰瀵硅薄杞?Long锛圢umber 鐩存帴杞崲锛屽瓧绗︿覆瀹归敊瑙ｆ瀽锛夈€?
     */
    private Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }

        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean asBoolean(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }

        String s = String.valueOf(o).toLowerCase();
        if ("true".equals(s)) {
            return Boolean.TRUE;
        }
        if ("false".equals(s)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * 浠绘剰瀵硅薄杞?List<String>锛堟敮鎸佸師鐢?List 涓庣畝鍗?JSON 鏁扮粍瀛楃涓诧級銆?
     */
    private List<String> asStringList(Object o) {
        if (o == null) {
            return Collections.emptyList();
        }

        if (o instanceof List<?> l) {
            List<String> out = new ArrayList<>(l.size());
            for (Object e : l) {
                if (e != null) {
                    out.add(String.valueOf(e));
                }
            }
            return out;
        }

        String s = String.valueOf(o);
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
            if (s.isBlank()) {
                return Collections.emptyList();
            }

            String[] parts = s.split(",");
            List<String> out = new ArrayList<>();
            for (String p : parts) {
                String t = p.trim();
                if (t.startsWith("\"") && t.endsWith("\"")) {
                    t = t.substring(1, t.length() - 1);
                }

                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
