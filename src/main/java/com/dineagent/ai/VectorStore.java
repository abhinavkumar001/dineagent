package com.dineagent.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny in-memory vector store: keeps normalised embeddings and returns cosine similarity.
 * For a real system this would be pgvector, Pinecone or Elasticsearch; the interface stays the same.
 */
public final class VectorStore {

    private final List<float[]> vectors = new ArrayList<>();

    public void addAll(List<float[]> embeddings) {
        embeddings.forEach(e -> vectors.add(normalise(e)));
    }

    public int size() {
        return vectors.size();
    }

    /** Cosine similarity of the query with every stored vector, in insertion order. */
    public double[] scores(float[] query) {
        float[] q = normalise(query);
        double[] out = new double[vectors.size()];
        for (int i = 0; i < vectors.size(); i++) {
            float[] v = vectors.get(i);
            double dot = 0;
            for (int j = 0; j < Math.min(v.length, q.length); j++) dot += v[j] * q[j];
            out[i] = dot;
        }
        return out;
    }

    static float[] normalise(float[] v) {
        double s = 0;
        for (float x : v) s += x * x;
        double n = Math.sqrt(s);
        float[] out = new float[v.length];
        if (n == 0) return out;
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / n);
        return out;
    }
}
