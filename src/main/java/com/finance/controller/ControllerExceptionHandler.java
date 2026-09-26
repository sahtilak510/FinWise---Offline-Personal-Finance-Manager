package com.finance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(IllegalArgumentException exception, Model model) {
        model.addAttribute("status", HttpStatus.BAD_REQUEST.value());
        model.addAttribute("statusCode", HttpStatus.BAD_REQUEST.value());
        model.addAttribute("error", "Bad Request");
        model.addAttribute("errorTitle", "Invalid request");
        model.addAttribute("message", exception.getMessage());
        model.addAttribute("errorMessage", exception.getMessage());
        model.addAttribute("errorDescription", "Check the requested record and try again.");
        model.addAttribute("requestPath", "");
        return "error/error";
    }
}
