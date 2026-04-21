package io.finett.droidclaw.model;

import io.finett.droidclaw.R;

public class FileAttachment {
    private String filename;
    private String originalName;
    private String absolutePath;
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

    public int getDisplayIconResId() {
        if (mimeType == null) return R.drawable.ic_attachment;
        if (mimeType.startsWith("image/")) return R.drawable.ic_file_image;
        if (mimeType.startsWith("text/")) return R.drawable.ic_file_text;
        if (mimeType.contains("pdf")) return R.drawable.ic_file_text;
        if (mimeType.contains("spreadsheet") || mimeType.contains("excel")) return R.drawable.ic_file_spreadsheet;
        if (mimeType.contains("document") || mimeType.contains("word")) return R.drawable.ic_file_text;
        if (mimeType.startsWith("video/")) return R.drawable.ic_file_video;
        if (mimeType.startsWith("audio/")) return R.drawable.ic_file_audio;
        if (mimeType.contains("zip") || mimeType.contains("archive") || mimeType.contains("rar") ||
            mimeType.contains("tar") || mimeType.contains("compressed")) return R.drawable.ic_file_archive;
        if (mimeType.contains("json") || mimeType.contains("xml")) return R.drawable.ic_file_text;
        return R.drawable.ic_attachment;
    }
}
