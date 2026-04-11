package io.finett.droidclaw.filesystem;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Manages file uploads from the device into the workspace uploads directory.
 * Handles copying files from content URIs (from Storage Access Framework) to the workspace.
 */
public class FileUploadManager {
    private static final String TAG = "FileUploadManager";
    private static final long MAX_UPLOAD_SIZE = 50 * 1024 * 1024; // 50MB

    private final Context context;
    private final File uploadsDir;

    public FileUploadManager(Context context, WorkspaceManager workspaceManager) {
        this.context = context;
        this.uploadsDir = workspaceManager.getUploadsDirectory();
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }
    }

    /**
     * Uploads a file from a content URI to the workspace uploads directory.
     *
     * @param uri         Content URI from ACTION_OPEN_DOCUMENT or similar
     * @param displayName Optional display name; if null, resolved from URI
     * @return UploadResult containing the filename, path, and MIME type
     */
    public UploadResult uploadFile(Uri uri, String displayName) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }

        // Resolve display name
        String name = displayName;
        if (name == null || name.isEmpty()) {
            name = resolveFileName(uri);
        }

        // Sanitize filename
        String safeName = sanitizeFilename(name);

        // Ensure unique filename by prepending UUID
        String extension = "";
        int dotIndex = safeName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = safeName.substring(dotIndex);
            safeName = safeName.substring(0, dotIndex);
        }
        String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "_" + safeName + extension;

        File destFile = new File(uploadsDir, uniqueName);

        // Check file size before copying
        long fileSize = getFileSize(uri);
        if (fileSize > MAX_UPLOAD_SIZE) {
            throw new IOException("File too large: " + formatSize(fileSize) + " (max: " + formatSize(MAX_UPLOAD_SIZE) + ")");
        }

        // Copy file to uploads directory
        copyUriToFile(uri, destFile);

        String mimeType = resolveMimeType(destFile.getName());

        Log.d(TAG, "File uploaded: " + name + " -> " + uniqueName + " (" + formatSize(destFile.length()) + ")");

        return new UploadResult(uniqueName, destFile.getAbsolutePath(), mimeType, name);
    }

    /**
     * Copies content from a URI to a destination file.
     */
    private void copyUriToFile(Uri uri, File destFile) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(destFile)) {

            if (inputStream == null) {
                throw new IOException("Could not open input stream for URI: " + uri);
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Resolves the file size from a content URI.
     */
    private long getFileSize(Uri uri) {
        long size = 0;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (!cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not resolve file size for URI: " + uri, e);
            }
        }
        return size;
    }

    /**
     * Resolves the original file name from a content URI.
     */
    private String resolveFileName(Uri uri) {
        String name = "uploaded_file";

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String displayName = cursor.getString(nameIndex);
                        if (displayName != null && !displayName.isEmpty()) {
                            name = displayName;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not resolve file name for URI: " + uri, e);
            }
        } else if ("file".equals(uri.getScheme())) {
            File file = new File(uri.getPath());
            name = file.getName();
        }

        return name;
    }

    /**
     * Resolves MIME type from filename extension.
     */
    public static String resolveMimeType(String filename) {
        String extension = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = filename.substring(dotIndex + 1).toLowerCase();
        }

        // Try Android's MimeTypeMap (available on device)
        try {
            MimeTypeMap mimeMap = MimeTypeMap.getSingleton();
            if (mimeMap != null) {
                String mimeType = mimeMap.getMimeTypeFromExtension(extension);
                if (mimeType != null && !mimeType.isEmpty()) {
                    return mimeType;
                }
            }
        } catch (Exception e) {
            // MimeTypeMap not available (e.g., in unit tests)
        }

        // Fallback: common extensions
        return getMimeTypeFromExtension(extension);
    }

    /**
     * Fallback MIME type mapping for common extensions (used when MimeTypeMap is unavailable).
     */
    private static String getMimeTypeFromExtension(String ext) {
        switch (ext) {
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            case "pdf": return "application/pdf";
            case "txt":
            case "md":
            case "csv":
            case "log": return "text/plain";
            case "html":
            case "htm": return "text/html";
            case "json": return "application/json";
            case "xml": return "application/xml";
            case "zip": return "application/zip";
            case "doc":
            case "docx": return "application/msword";
            case "xls":
            case "xlsx": return "application/vnd.ms-excel";
            case "py": return "text/x-python";
            case "js": return "text/javascript";
            case "java": return "text/x-java";
            case "kt": return "text/x-kotlin";
            case "mp3": return "audio/mpeg";
            case "mp4":
            case "avi":
            case "mov": return "video/mp4";
            default: return "application/octet-stream";
        }
    }

    /**
     * Sanitizes a filename to prevent path traversal and invalid characters.
     */
    private static String sanitizeFilename(String filename) {
        // Remove path components
        filename = new File(filename).getName();

        // Remove null bytes and control characters
        filename = filename.replaceAll("[\\x00-\\x1f]", "");

        // Remove leading/trailing dots and spaces
        filename = filename.replaceAll("^[.\\s]+|[.\\s]+$", "");

        if (filename.isEmpty()) {
            filename = "unnamed_file";
        }

        return filename;
    }

    /**
     * Checks if a file is an image that can be sent directly to a vision model.
     */
    public static boolean isVisionImage(String mimeType) {
        return mimeType != null && (mimeType.startsWith("image/png") ||
                mimeType.startsWith("image/jpeg") ||
                mimeType.startsWith("image/jpg") ||
                mimeType.startsWith("image/gif") ||
                mimeType.startsWith("image/webp"));
    }

    /**
     * Checks if a file is an image (by extension).
     */
    public static boolean isImageFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp");
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Result of a file upload operation.
     */
    public static class UploadResult {
        private final String filename;       // Unique name in uploads directory
        private final String absolutePath;   // Full path to the file
        private final String mimeType;       // Detected MIME type
        private final String originalName;   // Original display name from device

        public UploadResult(String filename, String absolutePath, String mimeType, String originalName) {
            this.filename = filename;
            this.absolutePath = absolutePath;
            this.mimeType = mimeType;
            this.originalName = originalName;
        }

        public String getFilename() {
            return filename;
        }

        public String getAbsolutePath() {
            return absolutePath;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getOriginalName() {
            return originalName;
        }

        public File getFile() {
            return new File(absolutePath);
        }

        public boolean isImage() {
            return isVisionImage(mimeType);
        }
    }
}
