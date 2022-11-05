package dev.karolkoltun.persistence.concurrency;

import java.io.Serial;

public class TaskStepExecutionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TaskStepExecutionException(Throwable cause, String threadName, int step) {
        super(String.format("Exception executing step #%s in thread %s.", step, threadName), cause);
    }
}
