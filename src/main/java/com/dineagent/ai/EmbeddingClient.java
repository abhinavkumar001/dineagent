package com.dineagent.ai;

import java.util.List;

/** Turns text into vectors. Implemented by Gemini; tests use a fake. */
public interface EmbeddingClient {

    boolean available();

    /** One vector per input text, in the same order. */
    List<float[]> embed(List<String> texts, boolean isQuery);
}
