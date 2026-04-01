package com.tongji.knowpost.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class KnowPostBodyResolver {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public KnowPostBodyResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String resolve(KnowPostDetailRow row) {
        if (row == null) {
            return "";
        }
        String remote = fetchRemote(row.getContentUrl());
        if (StringUtils.hasText(remote)) {
            return remote;
        }
        return buildDemoMarkdown(row);
    }

    private String fetchRemote(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
            ResponseEntity<byte[]> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] bytes = resp.getBody();
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            MediaType contentType = resp.getHeaders().getContentType();
            Charset charset = contentType != null && contentType.getCharset() != null ? contentType.getCharset() : StandardCharsets.UTF_8;
            String text = new String(bytes, charset).trim();
            return StringUtils.hasText(text) ? text : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String buildDemoMarkdown(KnowPostDetailRow row) {
        List<String> tags = parseTags(row.getTags());
        StringBuilder sb = new StringBuilder();
        String title = StringUtils.hasText(row.getTitle()) ? row.getTitle() : "未命名知文";
        sb.append("# ").append(title).append("\n\n");
        if (StringUtils.hasText(row.getDescription())) {
            sb.append("## 摘要\n");
            sb.append(row.getDescription()).append("\n\n");
        }
        sb.append("## 正文\n");
        sb.append("这篇文章围绕“").append(title).append("”展开，适合做项目中的 demo 正文展示。").append("\n\n");
        sb.append("### 核心要点\n");
        sb.append("- 主题：").append(title).append("\n");
        if (!tags.isEmpty()) {
            sb.append("- 标签：").append(String.join("、", tags)).append("\n");
        }
        sb.append("- 作者：").append(StringUtils.hasText(row.getAuthorNickname()) ? row.getAuthorNickname() : "匿名").append("\n\n");
        sb.append("### 说明\n");
        sb.append("当前环境下正文原始链接不可直接访问，因此这里使用服务端可读的演示正文作为替代。");
        return sb.toString();
    }

    private List<String> parseTags(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
