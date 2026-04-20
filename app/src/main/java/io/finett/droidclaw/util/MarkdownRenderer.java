package io.finett.droidclaw.util;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.widget.TextView;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

/**
 * Utility class for rendering markdown text in TextViews.
 * Uses Markwon library with support for:
 * - Basic markdown (bold, italic, headers, lists, links)
 * - Code blocks (monospace font, no syntax highlighting)
 * - Tables
 * - Strikethrough
 * - Task lists
 */
public class MarkdownRenderer {
    private static Markwon instance;

    /**
     * Get singleton Markwon instance configured with all plugins.
     *
     * @param context Android context
     * @return Configured Markwon instance
     */
    public static Markwon getInstance(Context context) {
        if (instance == null) {
            instance = Markwon.builder(context)
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(context))
                    .usePlugin(TaskListPlugin.create(context))
                    .build();
        }
        return instance;
    }

    /**
     * Render markdown text into a TextView.
     *
     * @param context  Android context
     * @param textView Target TextView
     * @param markdown Markdown text to render
     */
    public static void render(Context context, TextView textView, String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            textView.setText("");
            return;
        }
        
        getInstance(context).setMarkdown(textView, markdown);
    }

    /**
     * Convert markdown to Spanned (for compatibility with older code).
     *
     * @param context  Android context
     * @param markdown Markdown text
     * @return Spanned text with markdown formatting
     */
    public static CharSequence toSpanned(Context context, String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        
        return getInstance(context).toMarkdown(markdown);
    }

    /**
     * Check if text contains markdown syntax.
     * This is a simple heuristic check for common markdown patterns.
     *
     * @param text Text to check
     * @return true if text likely contains markdown
     */
    public static boolean containsMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        

        return text.contains("**") ||     // Bold
               text.contains("__") ||     // Bold
               text.contains("```") ||    // Code block
               text.contains("`") ||      // Inline code
               text.contains("# ") ||     // Headers
               text.contains("## ") ||    // Headers
               text.contains("### ") ||   // Headers
               text.contains("[") && text.contains("](") || // Links
               text.contains("- [") ||    // Task lists
               text.contains("~~") ||     // Strikethrough
               text.contains("| ") ||     // Tables
               text.matches("(?s).*^\\d+\\.\\s.*"); // Numbered lists (multiline)
    }
}