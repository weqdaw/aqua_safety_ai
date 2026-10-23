package com.hc.drown.module.task.service;

import com.hc.drown.common.exception.BusinessException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RiskTaskException extends BusinessException {
    private final HttpStatus httpStatus;

    public RiskTaskException(HttpStatus status, String message) {
        super(message);
        this.httpStatus = status;
    }
}
