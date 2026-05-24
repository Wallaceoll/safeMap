package com.safemap.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SenhasNaoConferemException extends RuntimeException {
    public SenhasNaoConferemException(String mensagem) {
        super(mensagem);
    }
}

