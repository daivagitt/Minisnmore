package com.miniandmore.integration.service;

public class ZohoBooksException extends RuntimeException {

    public ZohoBooksException(String message) {
        super(message);
    }

    public ZohoBooksException(String message, Throwable cause) {
        super(message, cause);
    }
}
