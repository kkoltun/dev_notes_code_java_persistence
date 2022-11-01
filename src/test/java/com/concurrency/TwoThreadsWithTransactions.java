package com.concurrency;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.concurrency.HibernateTest.awaitOnLatch;

public class TwoThreadsWithTransactions<T> {
    private static final Logger log = LoggerFactory.getLogger(TwoThreadsWithTransactions.class);

    private final EntityManagerFactory entityManagerFactory;
    private final Supplier<T> contextSupplier;
    private final String threadOneName;
    private final List<RunnableWithContext<T>> threadOneSteps;
    private final String threadTwoName;
    private final List<RunnableWithContext<T>> threadTwoSteps;

    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final CountDownLatch endLatch = new CountDownLatch(2);

    private TwoThreadsWithTransactions(EntityManagerFactory entityManagerFactory,
                                       Supplier<T> contextSupplier,
                                       String threadOneName,
                                       List<RunnableWithContext<T>> threadOneSteps,
                                       String threadTwoName,
                                       List<RunnableWithContext<T>> threadTwoSteps) {
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

    public void run() {
        int steps = threadOneSteps.size();
        List<CountDownLatch> threadOneLatches = IntStream.range(0, steps)
                .mapToObj(any -> new CountDownLatch(1))
                .collect(Collectors.toList());
        List<CountDownLatch> threadTwoLatches = IntStream.range(0, steps)
                .mapToObj(any -> new CountDownLatch(1))
                .collect(Collectors.toList());

        log.info("Configured with {} steps.", steps);

        new Thread(() -> doInHibernate(session -> {
            T threadContext = contextSupplier.get();

            log.info("{} INIT: Awaiting on the start", threadOneName);
            awaitOnLatch(startLatch);

            try {
                for (int i = 0; i < steps; ++i) {
                    if (i != 0) {
                        log.info("{} #{}: await", threadOneName, i);
                        awaitOnLatch(threadOneLatches.get(i));
                    }

                    log.info("{} #{} execute", threadOneName, i);
                    threadOneSteps.get(i).accept(session, threadContext);

                    // Let the thread #2 do the step i.
                    threadTwoLatches.get(i).countDown();
                }
            } finally {
                endLatch.countDown();
            }
        })).start();

        new Thread(() -> doInHibernate(session -> {
            T threadContext = contextSupplier.get();

            log.info("{} INIT: Awaiting on the start", threadTwoName);
            awaitOnLatch(startLatch);

            try {
                for (int i = 0; i < steps; ++i) {
                    log.info("{} #{}: await", threadTwoName, i);
                    awaitOnLatch(threadTwoLatches.get(i));

                    log.info("{} #{}: execute", threadTwoName, i);
                    threadTwoSteps.get(i).accept(session, threadContext);

                    if (i != steps - 1) {
                        threadOneLatches.get(i + 1).countDown();
                    }
                }
            } finally {
                endLatch.countDown();
            }
        })).start();

        log.info("Start threads");
        startLatch.countDown();
        awaitOnLatch(endLatch);
        log.info("Threads finished");
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
        private final List<RunnableWithContext<F>> threadOneSteps = new ArrayList<>();
        private String threadTwoName;
        private final List<RunnableWithContext<F>> threadTwoSteps = new ArrayList<>();

        private Builder(EntityManagerFactory entityManagerFactory, Supplier<F> contextSupplier) {
            this.entityManagerFactory = entityManagerFactory;
            this.contextSupplier = contextSupplier;
        }

        private ThreadTwoStepBuilder<F> addThreadOneStep(RunnableWithContext<F> runnable) {
            threadOneSteps.add(runnable);
            return new ThreadTwoStepBuilder<>(this);
        }

        private ThreadOneStepBuilder<F> addThreadTwoStep(RunnableWithContext<F> runnable) {
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

            public ThreadTwoStepBuilder<F> threadOneStartsWith(RunnableWithContext<F> step) {
                return builder.addThreadOneStep(step);
            }
        }

        public static class ThreadOneStepBuilder<F> {
            private final Builder<F> builder;

            public ThreadOneStepBuilder(Builder<F> builder) {
                this.builder = builder;
            }

            public ThreadTwoStepBuilder<F> thenThreadOne(RunnableWithContext<F> step) {
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

            public ThreadOneStepBuilder<F> thenThreadTwo(RunnableWithContext<F> step) {
                return builder.addThreadTwoStep(step);
            }
        }
    }
}
