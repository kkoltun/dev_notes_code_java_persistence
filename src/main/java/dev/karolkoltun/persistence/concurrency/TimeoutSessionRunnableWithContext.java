package dev.karolkoltun.persistence.concurrency;

import org.hibernate.Session;
import org.opentest4j.AssertionFailedError;

import java.time.Duration;
import java.util.concurrent.*;

public class TimeoutSessionRunnableWithContext<T> implements SessionRunnableWithContext<T> {
    private final Duration timeout;
    private final SessionRunnableWithContext<T> runnable;

    public TimeoutSessionRunnableWithContext(SessionRunnableWithContext<T> runnable, Duration timeout) {
        this.runnable = runnable;
        this.timeout = timeout;
    }

    @Override
    public void accept(Session session, T t) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            Future<?> future = executorService.submit(() -> runnable.accept(session, t));
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

            // Unexpected behavior.
            throw new AssertionFailedError(String.format("The expected timeout after %s did not happen.", timeout));
        } catch (TimeoutException exception) {
            // Expected behavior.
        } catch (ExecutionException | InterruptedException exception) {
            throw new RuntimeException(exception);
        } finally {
            executorService.shutdownNow();
        }
    }
}
