package com.dineagent.knowledge;

import com.dineagent.ai.EmbeddingClient;
import com.dineagent.ai.HybridRetriever;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The "R" in RAG for policy questions: finds the policy chunks most relevant to a question.
 * The LLM then answers using only these chunks and cites their source.
 */
@Service
public class PolicyRetriever {

    public record Retrieved(PolicyChunk chunk, double score) {}

    private static final double MIN_SCORE = 0.08;

    private final List<PolicyChunk> chunks;
    private final HybridRetriever retriever;

    public PolicyRetriever(ObjectMapper json, EmbeddingClient embeddings) {
        try (InputStream in = new ClassPathResource("data/policies.json").getInputStream()) {
            this.chunks = json.readValue(in, new TypeReference<List<PolicyChunk>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load data/policies.json", e);
        }
        this.retriever = new HybridRetriever(chunks.stream().map(c -> c.title() + ". " + c.text()).toList(), embeddings);
    }

    public List<Retrieved> retrieve(String question, int k) {
        return retriever.rank(question).stream()
                .filter(h -> h.score() >= MIN_SCORE)
                .limit(k)
                .map(h -> new Retrieved(chunks.get(h.index()), Math.round(h.score() * 1000) / 1000.0))
                .toList();
    }
}
