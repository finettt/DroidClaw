package io.finett.droidclaw.web;

/** Raised when a page cannot be fetched (SSRF block, bad status, oversized body, etc.). */
public class FetchException extends Exception {
    public FetchException(String message) {
        super(message);
    }

    public FetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
