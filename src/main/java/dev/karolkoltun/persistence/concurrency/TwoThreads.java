package dev.karolkoltun.persistence.concurrency;

import dev.karolkoltun.persistence.HibernateTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class TwoThreads<CTX> {
    private static final Logger log = LoggerFactory.getLogger(TwoThreads.class);

    private final Supplier<CTX> contextSupplier;
    private final String threadOneName;
    private final List<ThreadStep<Consumer<CTX>>> threadOneSteps = new ArrayList<>();
    private final String threadTwoName;
    private final List<ThreadStep<Consumer<CTX>>> threadTwoSteps = new ArrayList<>();
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final CountDownLatch stepsFinishedLatch = new CountDownLatch(2);
    private final CountDownLatch finishLatch = new CountDownLatch(2);

    private TwoThreads(Supplier<CTX> contextSupplier,
            String threadOneName,
            List<Consumer<CTX>> threadOneTasks,
            String threadTwoName,
            List<Consumer<CTX>> threadTwoTasks) {
        if (threadOneTasks.size() != threadTwoTasks.size()) {
            throw new IllegalArgumentException("Uneven number of steps.");
        }
        this.contextSupplier = contextSupplier;
        this.threadOneName = threadOneName;
        this.threadTwoName = threadTwoName;

        int steps = threadOneTasks.size();
        List<CountDownLatch> threadOneLatches = IntStream.range(0, steps)
                .mapToObj(__ -> new CountDownLatch(1))
                .toList();
        List<CountDownLatch> threadTwoLatches = IntStream.range(0, steps)
                .mapToObj(__ -> new CountDownLatch(1))
                .toList();

        for (int i = 0; i < steps; ++i) {
            CountDownLatch thisStepThreadOneLatch = i != 0
                    ? threadOneLatches.get(i)
                    : startLatch;
            CountDownLatch thisStepThreadTwoLatch = threadTwoLatches.get(i);

            CountDownLatch nextStepThreadOneLatch = i != (steps - 1)
                    ? threadOneLatches.get(i + 1)
                    : null;

            Runnable blockThreadOneUntilFired = () -> HibernateTest.awaitOnLatch(thisStepThreadOneLatch);
            Runnable blockThreadTwoUntilFired = () -> HibernateTest.awaitOnLatch(thisStepThreadTwoLatch);

            Runnable fireThreadOneNextStep = nextStepThreadOneLatch != null
                    ? nextStepThreadOneLatch::countDown
                    : () -> {
                    };
            Runnable fireThreadTwoCurrentStep = thisStepThreadTwoLatch::countDown;

            Consumer<CTX> threadOneTask = threadOneTasks.get(i);
            Consumer<CTX> threadTwoTask = threadTwoTasks.get(i);

            ThreadStep<Consumer<CTX>> threadOneStep = new ThreadStep<>(fireThreadTwoCurrentStep, blockThreadOneUntilFired, i, threadOneTask);
            ThreadStep<Consumer<CTX>> threadTwoStep = new ThreadStep<>(fireThreadOneNextStep, blockThreadTwoUntilFired, i, threadTwoTask);

            this.threadOneSteps.add(threadOneStep);
            this.threadTwoSteps.add(threadTwoStep);
        }
    }

    public void run() {
        int steps = threadOneSteps.size();

        log.info("Configured with {} steps.", steps);

        AtomicReference<TaskStepExecutionException> error = new AtomicReference<>();

        new ThreadWithSteps<>(threadOneName, error, threadOneSteps, contextSupplier, stepsFinishedLatch, finishLatch).start();
        new ThreadWithSteps<>(threadTwoName, error, threadTwoSteps, contextSupplier, stepsFinishedLatch, finishLatch).start();

        log.info("Start threads");
        startLatch.countDown();
        HibernateTest.awaitOnLatch(finishLatch);

        if (error.get() == null) {
            log.info("Threads finished successfully");
        } else {
            throw error.get();
        }
    }

    public static <G> Builder.StartBuilder<G> configure(Supplier<G> contextSupplier) {
        Builder<G> builder = new Builder<>(contextSupplier);
        return new Builder.StartBuilder<>(builder);
    }

    public static class Builder<F> {
        private final Supplier<F> contextSupplier;
        private final String threadOneName;
        private final List<Consumer<F>> threadOneSteps = new ArrayList<>();
        private final String threadTwoName;
        private final List<Consumer<F>> threadTwoSteps = new ArrayList<>();

        private Builder(Supplier<F> contextSupplier) {
            this.contextSupplier = contextSupplier;
            this.threadOneName = "T1";
            this.threadTwoName = "T2";
        }

        private ThreadTwoStepBuilder<F> addThreadOneStep(Consumer<F> runnable) {
            threadOneSteps.add(runnable);
            return new ThreadTwoStepBuilder<>(this);
        }

        private ThreadOneStepBuilder<F> addThreadTwoStep(Consumer<F> runnable) {
            threadTwoSteps.add(runnable);
            return new ThreadOneStepBuilder<>(this);
        }

        private TwoThreads<F> build() {
            return new TwoThreads<>(contextSupplier,
                    threadOneName,
                    threadOneSteps,
                    threadTwoName,
                    threadTwoSteps);
        }

        public static class StartBuilder<F> {
            private final Builder<F> builder;

            public StartBuilder(Builder<F> builder) {
                this.builder = builder;
            }

            public ThreadTwoStepBuilder<F> threadOneStartsWith(Consumer<F> step) {
                return builder.addThreadOneStep(step);
            }
        }

        public static class ThreadOneStepBuilder<F> {
            private final Builder<F> builder;

            public ThreadOneStepBuilder(Builder<F> builder) {
                this.builder = builder;
            }

            public ThreadTwoStepBuilder<F> thenThreadOne(Consumer<F> step) {
                return builder.addThreadOneStep(step);
            }

            public TwoThreads<F> build() {
                return builder.build();
            }

            public void run() {
                builder.build().run();
            }
        }

        public static class ThreadTwoStepBuilder<F> {
            private final Builder<F> builder;

            public ThreadTwoStepBuilder(Builder<F> builder) {
                this.builder = builder;
            }

            public ThreadOneStepBuilder<F> thenThreadTwo(Consumer<F> step) {
                return builder.addThreadTwoStep(step);
            }

            public ThreadOneStepBuilder<F> thenThreadTwoDoesNothing() {
                return builder.addThreadTwoStep(__ -> {});
            }
        }
    }
}
