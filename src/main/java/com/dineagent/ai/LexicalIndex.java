package com.dineagent.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TF-IDF keyword index. In hybrid retrieval it catches exact words ("paneer", "dosa")
 * that embeddings sometimes rank too low, and it is the fallback when embeddings are unavailable.
 */
public final class LexicalIndex {

    private final Map<String, Double> idf = new HashMap<>();
    private final List<Map<String, Double>> docs;

    public LexicalIndex(List<String> documents) {
        List<List<String>> tokenized = documents.stream().map(TextTokenizer::tokenize).toList();
        Map<String, Integer> df = new HashMap<>();
        tokenized.forEach(tokens -> tokens.stream().distinct().forEach(t -> df.merge(t, 1, Integer::sum)));
        int n = documents.size();
        df.forEach((term, count) -> idf.put(term, Math.log((1.0 + n) / (1.0 + count)) + 1.0));
        this.docs = tokenized.stream().map(this::weigh).toList();
    }

    /** Cosine similarity of the query with every document, in document order. */
    public double[] scores(String query) {
        Map<String, Double> q = weigh(TextTokenizer.tokenize(query));
        double[] out = new double[docs.size()];
        if (q.isEmpty()) {
            return out;
        }
        double qNorm = norm(q);
        for (int i = 0; i < docs.size(); i++) {
            Map<String, Double> d = docs.get(i);
            double dot = 0;
            for (Map.Entry<String, Double> e : q.entrySet()) {
                Double v = d.get(e.getKey());
                if (v != null) dot += e.getValue() * v;
            }
            out[i] = dot == 0 ? 0 : dot / (qNorm * norm(d));
        }
        return out;
    }

    private Map<String, Double> weigh(List<String> tokens) {
        Map<String, Double> v = new HashMap<>();
        for (String t : tokens) {
            if (idf.containsKey(t)) v.merge(t, 1.0, Double::sum);
        }
        int size = Math.max(tokens.size(), 1);
        v.replaceAll((t, c) -> (c / size) * idf.get(t));
        return v;
    }

    private static double norm(Map<String, Double> v) {
        double s = 0;
        for (double x : v.values()) s += x * x;
        return Math.sqrt(s);
    }
}
