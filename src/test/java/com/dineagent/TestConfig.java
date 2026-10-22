package com.dineagent;

import com.dineagent.ai.EmbeddingClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

@TestConfiguration
public class TestConfig {

    /** Primary LlmClient in tests (it implements LlmClient), replacing the real Gemini client. */
    @Bean
    @Primary
    public ScriptedLlm scriptedLlm() {
        return new ScriptedLlm();
    }

    /** No network in tests: retrieval runs on the keyword index only. */
    @Bean
    @Primary
    public EmbeddingClient noEmbeddings() {
        return new EmbeddingClient() {
            public boolean available() { return false; }
            public List<float[]> embed(List<String> texts, boolean isQuery) { throw new IllegalStateException("no embeddings in tests"); }
        };
    }
}
