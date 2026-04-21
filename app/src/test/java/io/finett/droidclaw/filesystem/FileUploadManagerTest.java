package io.finett.droidclaw.filesystem;

import org.junit.Test;

import static org.junit.Assert.*;

public class FileUploadManagerTest {

    @Test
    public void testResolveMimeType_Png() {
        assertEquals("image/png", FileUploadManager.resolveMimeType("test.png"));
    }

    @Test
    public void testResolveMimeType_Jpg() {
        assertEquals("image/jpeg", FileUploadManager.resolveMimeType("photo.jpg"));
    }

    @Test
    public void testResolveMimeType_Jpeg() {
        assertEquals("image/jpeg", FileUploadManager.resolveMimeType("photo.jpeg"));
    }

    @Test
    public void testResolveMimeType_Pdf() {
        assertEquals("application/pdf", FileUploadManager.resolveMimeType("document.pdf"));
    }

    @Test
    public void testResolveMimeType_Text() {
        assertEquals("text/plain", FileUploadManager.resolveMimeType("notes.txt"));
    }

    @Test
    public void testResolveMimeType_Json() {
        assertEquals("application/json", FileUploadManager.resolveMimeType("data.json"));
    }

    @Test
    public void testResolveMimeType_Unknown() {
        assertEquals("application/octet-stream", FileUploadManager.resolveMimeType("file.unknown"));
    }

    @Test
    public void testResolveMimeType_NoExtension() {
        assertEquals("application/octet-stream", FileUploadManager.resolveMimeType("Makefile"));
    }

    @Test
    public void testIsVisionImage_Png() {
        assertTrue(FileUploadManager.isVisionImage("image/png"));
    }

    @Test
    public void testIsVisionImage_Jpeg() {
        assertTrue(FileUploadManager.isVisionImage("image/jpeg"));
    }

    @Test
    public void testIsVisionImage_Gif() {
        assertTrue(FileUploadManager.isVisionImage("image/gif"));
    }

    @Test
    public void testIsVisionImage_Webp() {
        assertTrue(FileUploadManager.isVisionImage("image/webp"));
    }

    @Test
    public void testIsVisionImage_Pdf() {
        assertFalse(FileUploadManager.isVisionImage("application/pdf"));
    }

    @Test
    public void testIsVisionImage_Text() {
        assertFalse(FileUploadManager.isVisionImage("text/plain"));
    }

    @Test
    public void testIsVisionImage_Null() {
        assertFalse(FileUploadManager.isVisionImage(null));
    }

    @Test
    public void testIsImageFile_Png() {
        assertTrue(FileUploadManager.isImageFile("image.png"));
    }

    @Test
    public void testIsImageFile_Jpg() {
        assertTrue(FileUploadManager.isImageFile("photo.jpg"));
    }

    @Test
    public void testIsImageFile_Jpeg() {
        assertTrue(FileUploadManager.isImageFile("photo.jpeg"));
    }

    @Test
    public void testIsImageFile_Gif() {
        assertTrue(FileUploadManager.isImageFile("anim.gif"));
    }

    @Test
    public void testIsImageFile_Webp() {
        assertTrue(FileUploadManager.isImageFile("icon.webp"));
    }

    @Test
    public void testIsImageFile_Bmp() {
        assertTrue(FileUploadManager.isImageFile("bitmap.bmp"));
    }

    @Test
    public void testIsImageFile_Pdf() {
        assertFalse(FileUploadManager.isImageFile("doc.pdf"));
    }

    @Test
    public void testIsImageFile_Null() {
        assertFalse(FileUploadManager.isImageFile(null));
    }

    @Test
    public void testUploadResult_IsImage() {
        FileUploadManager.UploadResult imageResult = new FileUploadManager.UploadResult(
            "abc_img.png", "/path/abc_img.png", "image/png", "img.png");
        assertTrue(imageResult.isImage());

        FileUploadManager.UploadResult pdfResult = new FileUploadManager.UploadResult(
            "def_doc.pdf", "/path/def_doc.pdf", "application/pdf", "doc.pdf");
        assertFalse(pdfResult.isImage());
    }

    @Test
    public void testUploadResult_Getters() {
        FileUploadManager.UploadResult result = new FileUploadManager.UploadResult(
            "abc_test.txt", "/path/abc_test.txt", "text/plain", "test.txt");

        assertEquals("abc_test.txt", result.getFilename());
        assertEquals("test.txt", result.getOriginalName());
        assertEquals("/path/abc_test.txt", result.getAbsolutePath());
        assertEquals("text/plain", result.getMimeType());
    }
}
