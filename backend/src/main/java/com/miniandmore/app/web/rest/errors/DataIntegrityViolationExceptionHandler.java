package com.miniandmore.app.web.rest.errors;

import java.net.URI;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class DataIntegrityViolationExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return DataIntegrityViolationMapper
            .resolve(ex)
            .map(issue -> {
                ProblemDetail problem = ProblemDetail.forStatusAndDetail(issue.status(), issue.detail());
                problem.setTitle(issue.title());
                problem.setType(ErrorConstants.DEFAULT_TYPE);
                problem.setProperty("message", issue.messageKey());
                problem.setProperty("path", URI.create("about:blank"));
                return ResponseEntity.status(issue.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
            })
            .orElseGet(() ->
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(
                        ProblemDetail.forStatusAndDetail(
                            HttpStatus.CONFLICT,
                            "This value is already in use. Please choose a different one."
                        )
                    )
            );
    }
}
