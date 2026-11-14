package io.finett.droidclaw.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Tolerant Gson parser for a single GitHub release object. It reads
 * {@code {tag_name, name, html_url, body, prerelease, draft}} and never throws
 * on missing or null fields; anything absent is {@code null}/{@code false} on
 * the resulting {@link UpdateInfo}.
 *
 * <p>A release flagged as <em>both</em> a draft and a prerelease is a
 * not-yet-published build. The parser exposes this as {@link ParsedRelease#isSkip()}
 * so callers can skip it without surfacing a half-formed release.
 */
public class LatestReleaseParser {

    /**
     * Result of parsing one release object.
     *
     * <ul>
     *   <li>{@link #getInfo()} is {@code null} when the payload was empty,
     *       malformed, or carried no tag;</li>
     *   <li>{@link #isSkip()} is {@code true} only when the release is at once
     *       a draft and a prerelease.</li>
     * </ul>
     */
    public static final class ParsedRelease {
        private final UpdateInfo info;
        private final boolean skip;

        ParsedRelease(UpdateInfo info, boolean skip) {
            this.info = info;
            this.skip = skip;
        }

        /** The parsed release, or {@code null} when nothing usable was found. */
        public UpdateInfo getInfo() {
            return info;
        }

        /** @return {@code true} if the release is a draft and a prerelease and should be skipped. */
        public boolean isSkip() {
            return skip;
        }
    }

    /**
     * Parses the release JSON body.
     *
     * @param json the raw JSON of a single release object
     * @return the parsed release; never {@code null} (an empty/unparseable body
     *         yields a {@link ParsedRelease} whose {@link ParsedRelease#getInfo()}
     *         is {@code null})
     */
    public ParsedRelease parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ParsedRelease(null, false);
        }
        JsonObject obj;
        try {
            obj = JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            return new ParsedRelease(null, false);
        }

        String tag = optString(obj, "tag_name");
        if (tag == null || tag.isEmpty()) {
            // Without a tag there is no release to compare or display.
            return new ParsedRelease(null, false);
        }

        String title = optString(obj, "name");
        String htmlUrl = optString(obj, "html_url");
        String body = optString(obj, "body");
        boolean prerelease = optBoolean(obj, "prerelease");
        boolean draft = optBoolean(obj, "draft");

        UpdateInfo info = new UpdateInfo(tag, title, htmlUrl, body, prerelease);
        return new ParsedRelease(info, draft && prerelease);
    }

    private static String optString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private static boolean optBoolean(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsBoolean();
    }
}
