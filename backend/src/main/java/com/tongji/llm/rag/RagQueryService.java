package com.tongji.llm.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 单篇知文的 RAG 问答服务。
 * <p>
 * 负责先索引当前文章，再从向量库检索片段，最后调用大模型生成流式答案。
 */
@Service
@RequiredArgsConstructor
public class RagQueryService {
    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final RagIndexService indexService;

    /**
     * 返回流式问答结果。
     */
    public Flux<String> streamAnswerFlux(long postId, String question, int topK, int maxTokens) {
        try {
            indexService.ensureIndexed(postId);

            List<String> contexts = searchContexts(String.valueOf(postId), question, Math.max(1, topK));
            String context = String.join("\n\n---\n\n", contexts);

            String system = "You are a Chinese knowledge assistant. Answer only based on the provided context. If the answer is uncertain, say so clearly.";
            String user = "Question: " + question + "\n\nContext:\n" + context + "\n\nPlease answer in Chinese based on the context above.";

            return chatClient
                    .prompt()
                    .system(system)
                    .user(user)
                    .options(OpenAiChatOptions.builder()
                            .model("qwen-plus")
                            .temperature(0.2)
                            .maxTokens(maxTokens)
                            .build())
                    .stream()
                    .content()
                    .onErrorResume(e -> Flux.just(buildFallbackAnswer(question, contexts, e.getMessage())));
        } catch (Exception e) {
            log.error("RAG stream initialization failed for post {}", postId, e);
            return Flux.just(buildFallbackAnswer(question, List.of(), e.getMessage()));
        }
    }

    /**
     * 从向量库中检索与问题最相关的片段，只保留当前文章的数据。
     */
    private List<String> searchContexts(String postId, String query, int topK) {
        int fetchK = Math.max(topK * 3, 20);
        List<Document> docs;
        try {
            docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(fetchK).build()
            );
        } catch (Exception e) {
            log.warn("Vector similarity search failed for post {}: {}", postId, e.getMessage());
            return List.of();
        }
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        List<String> out = new ArrayList<>(topK);
        for (Document d : docs) {
            Object pid = d.getMetadata().get("postId");
            if (pid != null && postId.equals(String.valueOf(pid))) {
                String txt = d.getText();
                if (txt != null && !txt.isEmpty()) {
                    out.add(txt);
                    if (out.size() >= topK) {
                        break;
                    }
                }
            }
        }
        return out;
    }

    /**
     * 当模型调用或检索失败时，返回一个可展示的降级答案。
     */
    private String buildFallbackAnswer(String question, List<String> contexts, String errorMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前问答模型暂不可用，先根据正文片段给你一个简要结论。\n\n");
        if (question != null && !question.isBlank()) {
            sb.append("问题：").append(question).append("\n\n");
        }
        if (contexts == null || contexts.isEmpty()) {
            sb.append("没有检索到可用正文片段。\n");
        } else {
            sb.append("参考正文片段：\n");
            for (int i = 0; i < Math.min(2, contexts.size()); i++) {
                sb.append("- ").append(contexts.get(i).replace("\n", " ").trim()).append("\n");
            }
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            sb.append("\n错误信息：").append(errorMessage);
        }
        return sb.toString();
    }
}
