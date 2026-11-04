package com.example.test.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        ErrorResponse body = new ErrorResponse(e.getStatus(), e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                400, "MISSING_PARAMETER", "필수 파라미터가 없습니다: " + e.getParameterName()
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                400, "INVALID_PARAMETER", "파라미터 형식이 올바르지 않습니다: " + e.getName()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                400, "INVALID_BODY", "요청 본문을 읽을 수 없습니다"
        ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e){
        return ResponseEntity.status(405).body(new ErrorResponse(
                405, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다: " + e.getMethod()
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e){
        return ResponseEntity.status(404).body(new ErrorResponse(
                404, "NOT_FOUND", "요청한 주소를 찾을 수 없습니다"
        ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e){
        return ResponseEntity.status(415).body(new ErrorResponse(
                415, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다: " + e.getContentType()
        ));
    }

    // 그 밖의 모든 예외: 응답에는 일반 문구만, 예외(스택·SQL)는 서버 로그에만 남긴다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception e){
        log.error("처리하지 않은 예외", e);
        return ResponseEntity.status(500).body(new ErrorResponse(
                500, "INTERNAL_ERROR", "서버 오류가 발생했습니다"
        ));
    }
}
