package com.dineagent.llm;

import com.dineagent.ai.EmbeddingClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Talks to Google's Gemini REST API with plain java.net.http, no SDK:
 *  - generateContent with function declarations (tool calling)
 *  - batchEmbedContents for embeddings
 * Writing the HTTP calls by hand shows exactly how tool calling works on the wire.
 */
public class GeminiClient implements LlmClient, EmbeddingClient {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final HttpClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;
    private final String embeddingModel;
    private final Duration timeout;

    public GeminiClient(ObjectMapper json, String apiKey, String model, String embeddingModel, int timeoutSeconds) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.embeddingModel = embeddingModel;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String name() {
        return "gemini:" + model;
    }

    @Override
    public boolean available() {
        return !apiKey.isEmpty();
    }

    // ------------------------------------------------------------------ chat + tools

    @Override
    public LlmReply next(String systemPrompt, List<ChatMessage> history, List<ToolSpec> tools) {
        ObjectNode body = json.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemPrompt);

        ArrayNode contents = body.putArray("contents");
        for (ChatMessage m : history) {
            contents.add(toContent(m));
        }

        if (!tools.isEmpty()) {
            ArrayNode declarations = body.putArray("tools").addObject().putArray("functionDeclarations");
            for (ToolSpec t : tools) {
                ObjectNode d = declarations.addObject();
                d.put("name", t.name());
                d.put("description", t.description());
                d.set("parameters", json.valueToTree(t.parameters()));
            }
        }
        // Low temperature: an ordering agent should be predictable, not creative.
        body.putObject("generationConfig").put("temperature", 0.2);

        JsonNode response = post(model + ":generateContent", body);
        JsonNode candidate = response.path("candidates").path(0);
        JsonNode parts = candidate.path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            String reason = candidate.path("finishReason").asText(response.path("promptFeedback").path("blockReason").asText("unknown"));
            throw new LlmException("Gemini returned no content (" + reason + ")");
        }

        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode part : parts) {
            if (part.has("functionCall")) {
                JsonNode fc = part.get("functionCall");
                Map<String, Object> args = json.convertValue(fc.path("args"), new TypeReference<Map<String, Object>>() {});
                calls.add(new ToolCall(fc.path("name").asText(), args == null ? Map.of() : args));
            } else if (part.has("text") && !part.path("thought").asBoolean(false)) {
                text.append(part.get("text").asText());
            }
        }
        JsonNode usage = response.path("usageMetadata");
        return new LlmReply(text.toString().trim(), calls, parts,
                usage.path("promptTokenCount").asInt(0), usage.path("candidatesTokenCount").asInt(0));
    }

    private ObjectNode toContent(ChatMessage m) {
        ObjectNode c = json.createObjectNode();
        switch (m.role()) {
            case USER -> {
                c.put("role", "user");
                c.putArray("parts").addObject().put("text", m.text());
            }
            case MODEL -> {
                c.put("role", "model");
                if (m.raw() != null) {
                    c.set("parts", m.raw()); // send Gemini's own parts back unchanged (keeps thought signatures)
                } else {
                    ArrayNode parts = c.putArray("parts");
                    if (m.text() != null && !m.text().isBlank()) parts.addObject().put("text", m.text());
                    for (ToolCall call : m.toolCalls()) {
                        ObjectNode fc = parts.addObject().putObject("functionCall");
                        fc.put("name", call.name());
                        fc.set("args", json.valueToTree(call.args()));
                    }
                }
            }
            case TOOL -> {
                c.put("role", "user");
                ArrayNode parts = c.putArray("parts");
                for (ToolResult r : m.toolResults()) {
                    ObjectNode fr = parts.addObject().putObject("functionResponse");
                    fr.put("name", r.name());
                    fr.set("response", json.valueToTree(r.result()));
                }
            }
        }
        return c;
    }

    // ------------------------------------------------------------------ embeddings

    @Override
    public List<float[]> embed(List<String> texts, boolean isQuery) {
        List<float[]> out = new ArrayList<>();
        // The API accepts up to 100 texts per batch.
        for (int start = 0; start < texts.size(); start += 100) {
            List<String> batch = texts.subList(start, Math.min(start + 100, texts.size()));
            ObjectNode body = json.createObjectNode();
            ArrayNode requests = body.putArray("requests");
            for (String t : batch) {
                ObjectNode r = requests.addObject();
                r.put("model", "models/" + embeddingModel);
                r.putObject("content").putArray("parts").addObject().put("text", t);
                r.put("taskType", isQuery ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT");
            }
            JsonNode response = post(embeddingModel + ":batchEmbedContents", body);
            for (JsonNode e : response.path("embeddings")) {
                JsonNode values = e.path("values");
                float[] v = new float[values.size()];
                for (int i = 0; i < v.length; i++) v[i] = (float) values.get(i).asDouble();
                out.add(v);
            }
        }
        if (out.size() != texts.size()) {
            throw new LlmException("Expected " + texts.size() + " embeddings, got " + out.size());
        }
        return out;
    }

    // ------------------------------------------------------------------ http

    private JsonNode post(String path, ObjectNode body) {
        if (!available()) {
            throw new LlmException("GEMINI_API_KEY is not set");
        }
        // Free-tier keys hit rate limits (HTTP 429) easily; retry with backoff instead of failing the user.
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE + path + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if ((status == 429 || status == 503) && attempts < 4) {
                    Thread.sleep(2000L * attempts * attempts);
                    continue;
                }
                if (status / 100 != 2) {
                    String message;
                    try {
                        message = json.readTree(response.body()).path("error").path("message").asText(response.body());
                    } catch (IOException e) {
                        message = response.body();
                    }
                    throw new LlmException("Gemini HTTP " + status + ": " + message);
                }
                return json.readTree(response.body());
            } catch (IOException e) {
                throw new LlmException("Could not reach Gemini: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("Interrupted while calling Gemini", e);
            }
        }
    }
}
