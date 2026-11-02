package by.fk8ght.task.exception.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private Instant time;
    private int status;
    private String message;
    private String path;
}
