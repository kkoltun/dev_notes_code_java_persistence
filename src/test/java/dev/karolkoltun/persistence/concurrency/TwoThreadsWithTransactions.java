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
import java.util.function.Consumer;
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

    public void run() throws Throwable {
        int steps = threadOneSteps.size();

        log.info("Configured with {} steps.", steps);

        OptionalError error = new OptionalError();

        new Thread(executeTasks(error, threadOneName, threadOneSteps)).start();
        new Thread(executeTasks(error, threadTwoName, threadTwoSteps)).start();

        log.info("Start threads");
        startLatch.countDown();
        HibernateTest.awaitOnLatch(finishLatch);

        if (!error.isPresent()) {
            log.info("Threads finished successfully");
        } else {
            log.info("Thread {} failed in step {}. Message: {}.",
                    error.getThreadName(),
                    error.getStep(),
                    error.getThrowable().getMessage());
            throw error.getThrowable();
        }
    }

    private Runnable executeTasks(OptionalError error, String threadName, List<ThreadStep<T>> threadSteps) {
        return () -> doInHibernate(session -> {
            T threadContext = contextSupplier.get();

            for (ThreadStep<T> step : threadSteps) {
                String logPrefix = String.format(String.format("%s #%s:", threadName, step.getStepIndex() + 1));

                log.info("{} await", logPrefix);
                step.blockUntilFiredByAnotherThread();

                if (error.isPresent()) {
                    log.info("{} detected error in another thread; exit", logPrefix);
                    break;
                }

                try {
                    log.info("{} execute", logPrefix);
                    step.getTask().accept(session, threadContext);
                } catch (Throwable throwable) {
                    log.error("{} error", logPrefix);
                    error.set(threadName, step.getStepIndex(), throwable);
                    break;
                } finally {
                    step.fireNextStepOnAnotherThread();
                }
            }

            log.info("{} FINISH: Awaiting on the finish line", threadName);
            finishLatch.countDown();
            // Do not finish yet - another thread might still be running.
            HibernateTest.awaitOnLatch(finishLatch);

            log.info("{} FINISH: Finished", threadName);
        });
    }

    private void doInHibernate(Consumer<Session> callable) {
        Transaction transaction = null;
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {

            transaction = session.beginTransaction();

            callable.accept(session);

            if (session.isOpen()) {
                if (!transaction.getRollbackOnly()) {
                    transaction.commit();
                } else {
                    try {
                        transaction.rollback();
                    } catch (Exception e) {
                        System.err.println("Rollback failure: " + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                }
            }
        } catch (Throwable t) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception e) {
                    System.err.println("Rollback failure: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
            throw t;
        }
    }

    public static <G> Builder.StartBuilder<G> configure(EntityManagerFactory entityManagerFactory, Supplier<G> contextSupplier) {
        Builder<G> builder = new Builder<>(entityManagerFactory, contextSupplier);
        return new Builder.StartBuilder<>(builder);
    }

    public static class Builder<F> {
        private final EntityManagerFactory entityManagerFactory;
        private final Supplier<F> contextSupplier;
        private String threadOneName;
        private final List<SessionRunnableWithContext<F>> threadOneSteps = new ArrayList<>();
        private String threadTwoName;
        private final List<SessionRunnableWithContext<F>> threadTwoSteps = new ArrayList<>();

        private Builder(EntityManagerFactory entityManagerFactory, Supplier<F> contextSupplier) {
            this.entityManagerFactory = entityManagerFactory;
            this.contextSupplier = contextSupplier;
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
            if (threadOneName == null) {
                threadOneName = "T1";
            }
            if (threadTwoName == null) {
                threadTwoName = "T2";
            }

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

            public TwoThreadsWithTransactions<F> thenFinish() {
                return builder.build();
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

    static class OptionalError {
        private boolean present;
        private String threadName;
        private int step;
        private Throwable throwable;

        public OptionalError() {
            present = false;
        }

        public void set(String threadName, int step, Throwable throwable) {
            this.present = true;
            this.threadName = threadName;
            this.step = step;
            this.throwable = throwable;
        }

        public boolean isPresent() {
            return present;
        }

        public int getStep() {
            return step;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public String getThreadName() {
            return threadName;
        }
    }
}
