package dev.karolkoltun.persistence.concurrency;

import dev.karolkoltun.persistence.HibernateTest;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

// todo instead of throwing a Throwable from Runtime, it could just return a result:
//      either it was success (with context) or an Exception in step i
//      then in tests we could just check for the Exception in some step if we are expecting an error in one of the steps
//      then we could maybe use Vavr?
//      then we could use custom AsstertJ assertions
//
// todo write tests
// todo use nicer technology than a list of latches
// todo employ vavr just for the sake of learning it
public class TwoThreadsWithTransactions<T> {
    private static final Logger log = LoggerFactory.getLogger(TwoThreadsWithTransactions.class);

    static class ThreadStep<R> {
        private final Runnable fireNextStepRunnable;

        private final Runnable blockUntilFiredRunnable;
        private final int stepCount;
        private final SessionRunnableWithContext<R> task;

        public ThreadStep(Runnable fireNextStepRunnable, Runnable blockUntilFiredRunnable, int stepCount, SessionRunnableWithContext<R> task) {
            this.fireNextStepRunnable = fireNextStepRunnable;
            this.blockUntilFiredRunnable = blockUntilFiredRunnable;
            this.stepCount = stepCount;
            this.task = task;
        }

        public void fireNextStepOnAnotherThread() {
            fireNextStepRunnable.run();
        }

        public void blockUntilFiredByAnotherThread() {
            blockUntilFiredRunnable.run();
        }

        public int getStepIndex() {
            return stepCount;
        }

        public SessionRunnableWithContext<R> getTask() {
            return task;
        }
    }

    private final EntityManagerFactory entityManagerFactory;
    private final Supplier<T> contextSupplier;
    private final String threadOneName;
    private final List<ThreadStep<T>> threadOneSteps = new ArrayList<>();
    private final String threadTwoName;
    private final List<ThreadStep<T>> threadTwoSteps = new ArrayList<>();
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final CountDownLatch stepsFinishedLatch = new CountDownLatch(2);
    private final CountDownLatch finishLatch = new CountDownLatch(2);

    private TwoThreadsWithTransactions(EntityManagerFactory entityManagerFactory,
                                       Supplier<T> contextSupplier,
                                       String threadOneName,
                                       List<SessionRunnableWithContext<T>> threadOneTasks,
                                       String threadTwoName,
                                       List<SessionRunnableWithContext<T>> threadTwoTasks) {
        if (threadOneTasks.size() != threadTwoTasks.size()) {
            throw new IllegalArgumentException("Uneven number of steps.");
        }
        this.entityManagerFactory = entityManagerFactory;
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

            SessionRunnableWithContext<T> threadOneTask = threadOneTasks.get(i);
            SessionRunnableWithContext<T> threadTwoTask = threadTwoTasks.get(i);

            ThreadStep<T> threadOneStep = new ThreadStep<>(fireThreadTwoCurrentStep, blockThreadOneUntilFired, i, threadOneTask);
            ThreadStep<T> threadTwoStep = new ThreadStep<>(fireThreadOneNextStep, blockThreadTwoUntilFired, i, threadTwoTask);

            this.threadOneSteps.add(threadOneStep);
            this.threadTwoSteps.add(threadTwoStep);
        }
    }

    public void run() {
        int steps = threadOneSteps.size();

        log.info("Configured with {} steps.", steps);

        AtomicReference<TaskStepExecutionException> error = new AtomicReference<>();

        new Thread(() -> executeTasks(error, threadOneName, threadOneSteps)).start();
        new Thread(() -> executeTasks(error, threadTwoName, threadTwoSteps)).start();

        log.info("Start threads");
        startLatch.countDown();
        HibernateTest.awaitOnLatch(finishLatch);

        if (error.get() == null) {
            log.info("Threads finished successfully");
        } else {
            throw error.get();
        }
    }

    private void executeTasks(AtomicReference<TaskStepExecutionException> error, String threadName, List<ThreadStep<T>> threadSteps) {
        Transaction transaction = null;

        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {
            transaction = session.beginTransaction();

            T threadContext = contextSupplier.get();

            for (ThreadStep<T> step : threadSteps) {
                String logPrefix = String.format(String.format("%s #%s:", threadName, step.getStepIndex() + 1));

                log.info("{} await", logPrefix);
                step.blockUntilFiredByAnotherThread();

                if (error.get() != null) {
                    log.info("{} detected error in another thread; exit", logPrefix);
                    break;
                }

                try {
                    log.info("{} execute", logPrefix);
                    step.getTask().accept(session, threadContext);
                } catch (Throwable throwable) {
                    log.error("{} error", logPrefix);
                    error.set(new TaskStepExecutionException(throwable, threadName, step.getStepIndex()));
                    break;
                } finally {
                    step.fireNextStepOnAnotherThread();
                }
            }

            log.info("{} FINISH: Awaiting on the finish line for another thread", threadName);

            stepsFinishedLatch.countDown();
            // Do not finish yet - another thread might still be running.
            HibernateTest.awaitOnLatch(stepsFinishedLatch);

            // Now we can finalize the transaction - only after another thread finished too.
            commitOrRollback(transaction);

            log.info("{} FINISH: Finished", threadName);
        } catch (Throwable t) {
            tryRollback(transaction);
            throw t;
        } finally {
            // Whatever happened, do not let the main thread wait indefinitely.
            finishLatch.countDown();
        }
    }

    private static void commitOrRollback(Transaction transaction) {
        if (transaction.isActive()) {
            if (!transaction.getRollbackOnly()) {
                transaction.commit();
            } else {
                tryRollback(transaction);
            }
        }
    }

    private static void tryRollback(Transaction transaction) {
        if (transaction != null) {
            try {
                transaction.rollback();
            } catch (Exception e) {
                log.error("Rollback failure", e);
            }
        }
    }

    public static <G> Builder.StartBuilder<G> configure(EntityManagerFactory entityManagerFactory, Supplier<G> contextSupplier) {
        Builder<G> builder = new Builder<>(entityManagerFactory, contextSupplier);
        return new Builder.StartBuilder<>(builder);
    }

    public static class Builder<F> {
        private final EntityManagerFactory entityManagerFactory;
        private final Supplier<F> contextSupplier;
        private final String threadOneName;
        private final List<SessionRunnableWithContext<F>> threadOneSteps = new ArrayList<>();
        private final String threadTwoName;
        private final List<SessionRunnableWithContext<F>> threadTwoSteps = new ArrayList<>();

        private Builder(EntityManagerFactory entityManagerFactory, Supplier<F> contextSupplier) {
            this.entityManagerFactory = entityManagerFactory;
            this.contextSupplier = contextSupplier;
            this.threadOneName = "T1";
            this.threadTwoName = "T2";
        }

        private ThreadTwoStepBuilder<F> addThreadOneStep(SessionRunnableWithContext<F> runnable) {
            threadOneSteps.add(runnable);
            return new ThreadTwoStepBuilder<>(this);
        }

        private ThreadOneStepBuilder<F> addThreadTwoStep(SessionRunnableWithContext<F> runnable) {
            threadTwoSteps.add(runnable);
            return new ThreadOneStepBuilder<>(this);
        }

        private TwoThreadsWithTransactions<F> build() {
            return new TwoThreadsWithTransactions<>(entityManagerFactory,
                    contextSupplier,
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

            public ThreadTwoStepBuilder<F> threadOneStartsWith(SessionRunnableWithContext<F> step) {
                return builder.addThreadOneStep(step);
            }
        }

        public static class ThreadOneStepBuilder<F> {
            private final Builder<F> builder;

            public ThreadOneStepBuilder(Builder<F> builder) {
                this.builder = builder;
            }

            public ThreadTwoStepBuilder<F> thenThreadOne(SessionRunnableWithContext<F> step) {
                return builder.addThreadOneStep(step);
            }

            public ThreadTwoStepBuilder<F> thenThreadOneCommits() {
                return builder.addThreadOneStep((session, context) -> session.getTransaction().commit());
            }

            public TwoThreadsWithTransactions<F> build() {
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

            public ThreadOneStepBuilder<F> thenThreadTwo(SessionRunnableWithContext<F> step) {
                return builder.addThreadTwoStep(step);
            }

            public ThreadOneStepBuilder<F> thenThreadTwoTimeoutsOn(SessionRunnableWithContext<F> step, Duration duration) {
                return builder.addThreadTwoStep(new TimeoutSessionRunnableWithContext<>(step, duration));
            }
        }
    }
}
