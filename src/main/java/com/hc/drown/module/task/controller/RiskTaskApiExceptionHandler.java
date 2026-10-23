package com.hc.drown.module.task.controller;

import com.hc.drown.common.api.Result;
import com.hc.drown.module.task.service.RiskTaskException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = RiskTaskController.class)
public class RiskTaskApiExceptionHandler {
    @ExceptionHandler(RiskTaskException.class)
    public ResponseEntity<Result<Void>> risk(RiskTaskException e) {
        return failure(e.getHttpStatus(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> validation(MethodArgumentNotValidException e) {
        var error = e.getBindingResult().getFieldError();
        return failure(HttpStatus.BAD_REQUEST, error == null ? "请求参数不正确" : error.getField() + ": " + error.getDefaultMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> malformed(Exception e) {
        return failure(HttpStatus.BAD_REQUEST, "请求参数格式不正确，请检查JSON、数字和布尔值");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> denied(AccessDeniedException e) {
        return failure(HttpStatus.FORBIDDEN, "没有相关权限");
    }

    private ResponseEntity<Result<Void>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Result.failed(status.value(), message));
    }
}
