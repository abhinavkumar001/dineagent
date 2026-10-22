package com.dineagent.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hybrid retrieval = semantic (embeddings) + lexical (TF-IDF).
 *   score = 0.65 * cosine(embedding) + 0.35 * tfidf
 * Semantic search understands "something light for dinner"; lexical search guarantees
 * that an exact dish name like "bisi bele bath" ranks first. If embeddings fail, it falls back to lexical only.
 */
public final class HybridRetriever {

    public record Hit(int index, double score, double semantic, double lexical) {}

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);
    private static final double SEMANTIC_WEIGHT = 0.65;

    private final LexicalIndex lexical;
    private final VectorStore vectors = new VectorStore();
    private final EmbeddingClient embeddings;
    private boolean semanticReady;

    public HybridRetriever(List<String> documents, EmbeddingClient embeddings) {
        this.lexical = new LexicalIndex(documents);
        this.embeddings = embeddings;
        if (embeddings.available()) {
            try {
                vectors.addAll(embeddings.embed(documents, false));
                semanticReady = vectors.size() == documents.size();
            } catch (RuntimeException e) {
                log.warn("Embedding the documents failed, using keyword search only: {}", e.getMessage());
            }
        }
    }

    public boolean semanticEnabled() {
        return semanticReady;
    }

    /** All documents scored for the query, best first. */
    public List<Hit> rank(String query) {
        double[] lex = lexical.scores(query);
        double[] sem = new double[lex.length];
        boolean useSemantic = semanticReady;
        if (useSemantic) {
            try {
                sem = vectors.scores(embeddings.embed(List.of(query), true).get(0));
            } catch (RuntimeException e) {
                log.warn("Query embedding failed, keyword search only: {}", e.getMessage());
                useSemantic = false;
            }
        }
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < lex.length; i++) {
            double score = useSemantic ? SEMANTIC_WEIGHT * sem[i] + (1 - SEMANTIC_WEIGHT) * lex[i] : lex[i];
            hits.add(new Hit(i, score, useSemantic ? sem[i] : 0, lex[i]));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits;
    }
}
