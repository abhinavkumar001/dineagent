package com.dineagent.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lower-cases, splits on non-alphanumerics, drops stop words and applies a light stemmer. */
public final class TextTokenizer {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "am", "are", "to", "of", "in", "on", "at", "for", "and", "or", "it", "this",
            "that", "i", "me", "my", "we", "you", "your", "can", "do", "does", "how", "what", "with", "from",
            "some", "something", "want", "would", "like", "please", "get", "give", "show", "find", "any", "be");

    private TextTokenizer() {
    }

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).replace("'", "").split("[^a-z0-9]+")) {
            if (raw.length() >= 2 && !STOP_WORDS.contains(raw)) {
                tokens.add(stem(raw));
            }
        }
        return tokens;
    }

    static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 3 && w.endsWith("es") && !w.endsWith("ses")) return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }
}
