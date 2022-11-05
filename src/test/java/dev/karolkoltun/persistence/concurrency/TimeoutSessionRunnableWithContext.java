package dev.karolkoltun.persistence.concurrency;

import org.hibernate.Session;
import org.opentest4j.AssertionFailedError;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class TimeoutSessionRunnableWithContext<T> implements SessionRunnableWithContext<T> {
    private final Duration timeout;
    private final SessionRunnableWithContext<T> runnable;

    public TimeoutSessionRunnableWithContext(SessionRunnableWithContext<T> runnable, Duration timeout) {
        this.runnable = runnable;
        this.timeout = timeout;
    }

    @Override
    public void accept(Session session, T t) {
        try {
            assertTimeoutPreemptively(timeout, () -> runnable.accept(session, t));
        } catch (AssertionFailedError error) {
            // This is expected
            return;
        }
        throw new AssertionFailedError(String.format("The expected timeout after %s did not happen.", timeout));
    }
}
