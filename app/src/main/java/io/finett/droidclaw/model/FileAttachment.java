package io.finett.droidclaw.model;

/**
 * Represents a file attachment associated with a chat message.
 */
public class FileAttachment {
    private String filename;       // Name stored in uploads directory
    private String originalName;   // Original display name
    private String absolutePath;   // Full file path
    private String mimeType;

    public FileAttachment(String filename, String originalName, String absolutePath, String mimeType) {
        this.filename = filename;
        this.originalName = originalName;
        this.absolutePath = absolutePath;
        this.mimeType = mimeType;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public String getDisplayIcon() {
        if (mimeType == null) return "📎";
        if (mimeType.startsWith("image/")) return "🖼️";
        if (mimeType.startsWith("text/")) return "📄";
        if (mimeType.contains("pdf")) return "📕";
        if (mimeType.contains("spreadsheet") || mimeType.contains("excel")) return "📊";
        if (mimeType.contains("document") || mimeType.contains("word")) return "📘";
        if (mimeType.startsWith("video/")) return "🎬";
        if (mimeType.startsWith("audio/")) return "🎵";
        if (mimeType.contains("zip") || mimeType.contains("archive") || mimeType.contains("rar") ||
            mimeType.contains("tar") || mimeType.contains("compressed")) return "📦";
        if (mimeType.contains("json") || mimeType.contains("xml")) return "📋";
        return "📎";
    }
}
