package io.finett.droidclaw.util;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight, dependency-free syntax highlighter for source files.
 * <p>
 * Tokenizes text into comments, strings, numbers, keywords and function calls
 * and applies foreground colors via {@link ForegroundColorSpan}. Language is
 * detected from the file extension. The tokenizer is deliberately simple
 * (regex-free scanner) so it stays fast and self-contained; it is good enough
 * for editing/viewing code, not a full language parser.
 */
public final class SyntaxHighlighter {

    // Colors chosen to read clearly against the default (light) background.
    private static final int COMMENT = 0xFF6B7B8D;
    private static final int STRING = 0xFF19803F;
    private static final int NUMBER = 0xFF986801;
    private static final int KEYWORD = 0xFF7C3AED;
    private static final int FUNCTION = 0xFF2563EB;

    private SyntaxHighlighter() {
    }

    /**
     * Highlight the given source text for the language implied by {@code extension}.
     *
     * @param context    Android context (only used for color resources)
     * @param extension  file extension without the leading dot (may be null)
     * @param source     the raw file content
     * @return a {@link SpannableStringBuilder} ready to set on a TextView
     */
    public static SpannableStringBuilder highlight(Context context, String extension, String source) {
        if (source == null || source.isEmpty()) {
            return new SpannableStringBuilder("");
        }

        Set<String> keywords = keywordsFor(extension);

        SpannableStringBuilder builder = new SpannableStringBuilder(source);
        int len = source.length();
        int i = 0;

        while (i < len) {
            char c = source.charAt(i);
            char next = (i + 1 < len) ? source.charAt(i + 1) : '\0';

            // Line comment: // or #
            if ((c == '/' && next == '/') || (c == '#')) {
                int end = source.indexOf('\n', i);
                if (end == -1) {
                    end = len;
                }
                apply(builder, i, end, COMMENT);
                i = end;
                continue;
            }

            // Block comment: /* ... */
            if (c == '/' && next == '*') {
                int end = source.indexOf("*/", i + 2);
                end = (end == -1) ? len : end + 2;
                apply(builder, i, end, COMMENT);
                i = end;
                continue;
            }

            // String literal
            if (c == '"' || c == '\'') {
                int end = scanString(source, i, c);
                apply(builder, i, end, STRING);
                i = end;
                continue;
            }

            // Number
            if (Character.isDigit(c)) {
                int end = i + 1;
                while (end < len && isTokenChar(source.charAt(end))) {
                    end++;
                }
                apply(builder, i, end, NUMBER);
                i = end;
                continue;
            }

            // Identifier / keyword / function call
            if (isIdentStart(c)) {
                int end = i + 1;
                while (end < len && isTokenChar(source.charAt(end))) {
                    end++;
                }
                String word = source.substring(i, end);
                if (keywords != null && keywords.contains(word)) {
                    apply(builder, i, end, KEYWORD);
                } else if (end < len && source.charAt(end) == '(') {
                    apply(builder, i, end, FUNCTION);
                }
                i = end;
                continue;
            }

            i++;
        }

        return builder;
    }

    /**
     * Detect a keyword map for the given extension. Returns null for unknown
     * extensions, in which case only comments/strings/numbers are colored.
     */
    private static Set<String> keywordsFor(String extension) {
        if (extension == null) {
            return null;
        }
        switch (extension.toLowerCase()) {
            case "java":
            case "kt":
            case "kts":
            case "scala":
            case "c":
            case "h":
            case "cpp":
            case "hpp":
            case "cs":
            case "go":
            case "rs":
                return JAVA_KEYWORDS;
            case "py":
            case "pyw":
                return PYTHON_KEYWORDS;
            case "js":
            case "jsx":
            case "ts":
            case "tsx":
            case "sh":
            case "bash":
            case "json":
            case "yaml":
            case "yml":
            case "toml":
                return GENERIC_KEYWORDS;
            default:
                return null;
        }
    }

    private static int scanString(String source, int start, char quote) {
        int i = start + 1;
        int len = source.length();
        while (i < len) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i++;
        }
        return len;
    }

    private static boolean isTokenChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '$';
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static void apply(SpannableStringBuilder builder, int start, int end, int color) {
        if (start >= end) {
            return;
        }
        builder.setSpan(new ForegroundColorSpan(color), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>();
    static {
        for (String kw : new String[]{
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                "class", "const", "continue", "default", "do", "double", "else", "enum",
                "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native", "new", "package",
                "private", "protected", "public", "return", "short", "static", "strictfp",
                "super", "switch", "synchronized", "this", "throw", "throws", "try",
                "void", "volatile", "while", "true", "false", "null", "var", "val",
                "fun", "data", "object", "companion", "override", "open", "lateinit",
                "in", "is", "as", "public", "private", "protected", "when", "init"}) {
            JAVA_KEYWORDS.add(kw);
        }
    }

    private static final Set<String> PYTHON_KEYWORDS = new HashSet<>();
    static {
        for (String kw : new String[]{
                "False", "None", "True", "and", "as", "assert", "async", "await",
                "break", "class", "continue", "def", "del", "elif", "else", "except",
                "finally", "for", "from", "global", "if", "import", "in", "is",
                "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
                "while", "with", "yield", "print", "self"}) {
            PYTHON_KEYWORDS.add(kw);
        }
    }

    private static final Set<String> GENERIC_KEYWORDS = new HashSet<>();
    static {
        for (String kw : new String[]{
                "var", "let", "const", "function", "return", "if", "else", "for",
                "while", "switch", "case", "default", "break", "continue", "new",
                "class", "extends", "import", "export", "from", "as", "try", "catch",
                "finally", "throw", "typeof", "null", "undefined", "true", "false",
                "in", "of", "this", "super", "public", "private", "static"}) {
            GENERIC_KEYWORDS.add(kw);
        }
    }
}
