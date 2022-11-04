package com.concurrency;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.concurrency.HibernateTest.awaitOnLatch;

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

    private final EntityManagerFactory entityManagerFactory;
    private final Supplier<T> contextSupplier;
    private final String threadOneName;
    private final List<SessionRunnableWithContext<T>> threadOneSteps;
    private final String threadTwoName;
    private final List<SessionRunnableWithContext<T>> threadTwoSteps;

    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final CountDownLatch finishLatch = new CountDownLatch(2);
    private final CountDownLatch threadsFinishedLatch = new CountDownLatch(2);

    private TwoThreadsWithTransactions(EntityManagerFactory entityManagerFactory,
            Supplier<T> contextSupplier,
            String threadOneName,
            List<SessionRunnableWithContext<T>> threadOneSteps,
            String threadTwoName,
            List<SessionRunnableWithContext<T>> threadTwoSteps) {
        if (threadOneSteps.size() != threadTwoSteps.size()) {
            throw new IllegalArgumentException("Uneven number of steps.");
        }
        this.entityManagerFactory = entityManagerFactory;
        this.contextSupplier = contextSupplier;
        this.threadOneName = threadOneName;
        this.threadOneSteps = threadOneSteps;
        this.threadTwoName = threadTwoName;
        this.threadTwoSteps = threadTwoSteps;
    }

    public void run() throws Throwable {
        int steps = threadOneSteps.size();
        List<CountDownLatch> threadOneLatches = IntStream.range(0, steps)
                .mapToObj(any -> new CountDownLatch(1))
                .collect(Collectors.toList());
        List<CountDownLatch> threadTwoLatches = IntStream.range(0, steps)
                .mapToObj(any -> new CountDownLatch(1))
                .collect(Collectors.toList());

        log.info("Configured with {} steps.", steps);

        // todo This is bad. If you are a recruiter, please note - I know it is bad, but it is 11 PM here, I want to get some sleep.
        final List<Error> error = new ArrayList<>();

        new Thread(() -> doInHibernate(session -> {
            T threadContext = contextSupplier.get();

            log.info("{} INIT: Awaiting on the start", threadOneName);
            awaitOnLatch(startLatch);

            for (int i = 0; i < steps; ++i) {
                try {
                    if (i != 0) {
                        log.info("{} #{}: await", threadOneName, i);
                        awaitOnLatch(threadOneLatches.get(i));
                    }

                    if (!error.isEmpty()) {
                        log.info("{} #{}: detected error in another thread; exit}", threadOneName, i);

                        // Thread #2 should already be dead, so unlock shutdown.
                        threadsFinishedLatch.countDown();
                        return;
                    }

                    log.info("{} #{} execute", threadOneName, i);
                    threadOneSteps.get(i).accept(session, threadContext);

                    // Let the thread #2 do the step i.
                    threadTwoLatches.get(i).countDown();
                } catch (Throwable throwable) {
                    log.info("{} #{} error {}", threadOneName, i, throwable.getMessage());
                    error.add(new Error(i, throwable, true));

                    // Unlock thread #2.
                    threadTwoLatches.get(i).countDown();

                    // Unlock shutdown
                    finishLatch.countDown();
                    threadsFinishedLatch.countDown();
                    return;
                }
            }

            log.info("{} FINISH: Awaiting on the finish", threadOneName);
            finishLatch.countDown();
            awaitOnLatch(finishLatch);

            threadsFinishedLatch.countDown();

            log.info("{} FINISH: Finished", threadOneName);
        })).start();

        new Thread(() -> doInHibernate(session -> {
            T threadContext = contextSupplier.get();

            log.info("{} INIT: Awaiting on the start", threadTwoName);
            awaitOnLatch(startLatch);

            for (int i = 0; i < steps; ++i) {
                try {
                    log.info("{} #{}: await", threadTwoName, i);
                    awaitOnLatch(threadTwoLatches.get(i));

                    if (!error.isEmpty()) {
                        log.info("{} #{}: detected error in another thread; exit}", threadTwoName, i);

                        // Thread #1 should already be dead, so unlock shutdown.
                        threadsFinishedLatch.countDown();
                        return;
                    }

                    log.info("{} #{}: execute", threadTwoName, i);
                    threadTwoSteps.get(i).accept(session, threadContext);

                    if (i != steps - 1) {
                        threadOneLatches.get(i + 1).countDown();
                    }
                } catch (Throwable throwable) {
                    log.info("{} #{} error {}", threadTwoName, i, throwable.getMessage());
                    error.add(new Error(i, throwable, false));

                    // Unlock thread #1 if neccessary.
                    if (i != steps - 1) {
                        threadOneLatches.get(i + 1).countDown();
                    }

                    // Unlock shutdown
                    finishLatch.countDown();
                    threadsFinishedLatch.countDown();
                    return;
                }
            }

            log.info("{} FINISH: Awaiting on the finish", threadTwoName);
            finishLatch.countDown();
            awaitOnLatch(finishLatch);

            threadsFinishedLatch.countDown();

            log.info("{} FINISH: Finished", threadTwoName);
        })).start();

        log.info("Start threads");
        startLatch.countDown();
        awaitOnLatch(threadsFinishedLatch);

        if (error.isEmpty()) {
            log.info("Threads finished successfully");
        } else {
            Error caughtError = error.get(0);
            log.info("Thread {} failed in step {}. Message: {}.", caughtError.firstThread ? "1" : "2", caughtError.step, caughtError.throwable.getMessage());
            throw caughtError.throwable;
        }
    }

    private void doInHibernate(Consumer<Session> callable) {
        Session session = null;
        Transaction transaction = null;
        try {
            session = entityManagerFactory.unwrap(SessionFactory.class).openSession();

            transaction = session.beginTransaction();

            callable.accept(session);

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
        } finally {
            if (session != null) {
                session.close();
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
            return new ThreadOneStepBuilder(this);
        }

        private void setThreadOneName(String threadOneName) {
            this.threadOneName = threadOneName;
        }

        private void setThreadTwoName(String threadTwoName) {
            this.threadTwoName = threadTwoName;
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

            public StartBuilder<F> threadOneName(String name) {
                builder.setThreadOneName(name);
                return this;
            }

            public StartBuilder<F> threadTwoName(String name) {
                builder.setThreadTwoName(name);
                return this;
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

            public ThreadTwoStepBuilder<F> thenThreadOneTimeoutsOn(SessionRunnableWithContext<F> step, Duration duration) {
                return builder.addThreadOneStep(new TimeoutSessionRunnableWithContext<>(step, duration));
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

    static class Error {
        private final int step;
        private final Throwable throwable;
        private final boolean firstThread;

        public Error(int step, Throwable throwable, boolean firstThread) {
            this.step = step;
            this.throwable = throwable;
            this.firstThread = firstThread;
        }
    }
}
