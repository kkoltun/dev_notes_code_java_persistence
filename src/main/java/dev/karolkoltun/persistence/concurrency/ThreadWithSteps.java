package dev.karolkoltun.persistence.concurrency;

import dev.karolkoltun.persistence.HibernateTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ThreadWithSteps<CTX> extends Thread {
    private static final Logger log = LoggerFactory.getLogger(ThreadWithSteps.class);

    private final AtomicReference<TaskStepExecutionException> error;
    private final List<ThreadStep<Consumer<CTX>>> steps;
    private final Supplier<CTX> contextSupplier;
    private final CountDownLatch stepsFinishedLatch;
    private final CountDownLatch finishLatch;

    public ThreadWithSteps(String name, AtomicReference<TaskStepExecutionException> error, List<ThreadStep<Consumer<CTX>>> steps, Supplier<CTX> contextSupplier,
            CountDownLatch stepsFinishedLatch, CountDownLatch finishLatch) {
        super(name);
        this.error = error;
        this.steps = steps;
        this.contextSupplier = contextSupplier;
        this.stepsFinishedLatch = stepsFinishedLatch;
        this.finishLatch = finishLatch;
    }

    @Override
    public void run() {
        executeTasks();
    }

    private void executeTasks() {
        try {
            CTX threadContext = contextSupplier.get();

            for (ThreadStep<Consumer<CTX>> step : steps) {
                String logPrefix = String.format(String.format("%s #%s:", this.getName(), step.getStepIndex() + 1));

                log.info("{} await", logPrefix);
                step.blockUntilFiredByAnotherThread();

                if (error.get() != null) {
                    log.info("{} detected error in another thread; exit", logPrefix);
                    break;
                }

                try {
                    log.info("{} execute", logPrefix);
                    step.getTask().accept(threadContext);
                } catch (Throwable throwable) {
                    log.error("{} error", logPrefix);
                    error.set(new TaskStepExecutionException(throwable, this.getName(), step.getStepIndex()));
                    break;
                } finally {
                    step.fireNextStepOnAnotherThread();
                }
            }

            log.info("{} FINISH: Awaiting on the finish line for another thread", this.getName());

            stepsFinishedLatch.countDown();
            // Do not finish yet - another thread might still be running.
            HibernateTest.awaitOnLatch(stepsFinishedLatch);

            log.info("{} FINISH: Finished", this.getName());
        } finally {
            // Whatever happened, do not let the main thread wait indefinitely.
            finishLatch.countDown();
        }
    }
}
