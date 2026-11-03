package com.example.test.common;

public record ErrorResponse(int status,
                            String code,
                            String message) {
}
