package com.example.rag.exception;

public class QueryProcessingException extends RuntimeException {
    public QueryProcessingException(String message) {
        super(message);
    }
    public QueryProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}