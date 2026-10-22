package com.dineagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * GeminiClient implements both LlmClient and EmbeddingClient, so one bean serves the agent and the retriever.
 * Tests replace it with @Primary fakes.
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public GeminiClient geminiClient(ObjectMapper json,
                                     @Value("${app.llm.gemini.api-key:}") String apiKey,
                                     @Value("${app.llm.gemini.model}") String model,
                                     @Value("${app.llm.gemini.embedding-model}") String embeddingModel,
                                     @Value("${app.llm.gemini.timeout-seconds:30}") int timeout,
                                     @Value("${app.llm.provider:auto}") String provider) {
        GeminiClient client = new GeminiClient(json, "offline".equalsIgnoreCase(provider) ? "" : apiKey,
                model, embeddingModel, timeout);
        if (client.available()) {
            log.info("Using {} for the agent and {} for embeddings", model, embeddingModel);
        } else {
            log.warn("GEMINI_API_KEY is not set. The chat will ask you to add a key; menu search uses keywords only.");
        }
        return client;
    }

    @Bean
    public Clock clock(@Value("${app.timezone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
