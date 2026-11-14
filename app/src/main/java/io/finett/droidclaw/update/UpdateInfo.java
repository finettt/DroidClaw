package io.finett.droidclaw.update;

/**
 * Immutable snapshot of a GitHub release, as surfaced by the in-app update
 * checker. All fields are optional on the wire; absent values arrive as
 * {@code null} (or {@code false} for {@link #isPrerelease()}).
 */
public final class UpdateInfo {

    private final String tag;
    private final String title;
    private final String htmlUrl;
    private final String body;
    private final boolean prerelease;

    public UpdateInfo(String tag, String title, String htmlUrl, String body, boolean prerelease) {
        this.tag = tag;
        this.title = title;
        this.htmlUrl = htmlUrl;
        this.body = body;
        this.prerelease = prerelease;
    }

    /** The release tag, e.g. {@code "2026.10.10"}. */
    public String getTag() {
        return tag;
    }

    /** The release display name (the {@code name} field). */
    public String getTitle() {
        return title;
    }

    /** The GitHub release page URL (the {@code html_url} field). */
    public String getHtmlUrl() {
        return htmlUrl;
    }

    /** The release notes body (the {@code body} field), may be empty or {@code null}. */
    public String getBody() {
        return body;
    }

    /** @return {@code true} if GitHub flagged this release as a prerelease. */
    public boolean isPrerelease() {
        return prerelease;
    }
}
