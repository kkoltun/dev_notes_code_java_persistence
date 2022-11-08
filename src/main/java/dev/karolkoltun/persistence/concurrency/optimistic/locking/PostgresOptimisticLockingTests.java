package dev.karolkoltun.persistence.concurrency.optimistic.locking;

import dev.karolkoltun.persistence.DataSourceProvider;
import dev.karolkoltun.persistence.HibernateTest;
import dev.karolkoltun.persistence.PostgresqlHrDataSourceProvider;
import dev.karolkoltun.persistence.concurrency.SessionRunnableWithContext;
import dev.karolkoltun.persistence.concurrency.TwoThreadsWithTransactions;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static dev.karolkoltun.persistence.entity.JobId.IT_PROG;
import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static java.sql.Connection.TRANSACTION_REPEATABLE_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

public class PostgresOptimisticLockingTests extends HibernateTest {

    @Override
    public DataSourceProvider dataSourceProvider() {
        return new PostgresqlHrDataSourceProvider();
    }

    @Override
    public boolean recreateBeforeEachTest() {
        // Reuse the data that came with the database.
        return false;
    }

    @Test
    void postgresDetectsSimultaneousChangesAndThrowsErrorInRepeatableReadIsolation() {
        Work updateProgrammersEmailWithRepeatableReadIsolationLevel = conn -> PostgresOptimisticLockingTests.updateProgrammerEmails(conn,
                TRANSACTION_REPEATABLE_READ);

        SessionRunnableWithContext<CompletableFutureContext> runAsynchronousUpdateOfProgrammersDepartment = (session, context) -> {
            // We need to do this in another thread and check the result afterwards.
            // Otherwise, this update will block and wait until T1 finishes their transaction. T1 will not finish because it is waiting for green light from this thread (T2).
            // Simple deadlock situation.
            //
            // So basically this is the expected flow using the future asynchronous task:
            // 1. The update in the CompletableFuture [FUT] will start now and immediately block, because T1 is holding a lock on these employees.
            // 2. In the meantime, this lambda ends, [T2] finishes this step and passes execution to [T1].
            // 3. [T1] commits the transaction, thus releasing the lock.
            // 4. [FUT] can now acquire the lock and (hopefully) will fail because it is updating outdated rows.
            //
            // What can go wrong? There is no guarantee that [FUT] update will actually fire *before* [T1] commits, even though this is the critical condition of the test.
            // So, theoretically [FUT] could execute update on "fresh" rows and this will succeed, because there is no concurrent modification then (simple serial execution [T1], [FUT]).
            // But then, [FUT] will not throw an error, this will be detected by an assertion below and the test will fail.
            // We could theoretically lock this thread [T2] until [FUT] is *just before* running the update, but this would be an overkill.
            context.completableFuture = CompletableFuture.runAsync(() -> session.doWork(updateProgrammersEmailWithRepeatableReadIsolationLevel),
                    Executors.newSingleThreadExecutor());
        };

        SessionRunnableWithContext<CompletableFutureContext> assertThatUpdateFailedOnConcurrentModificationException = (session, context) -> {
            ExecutionException updateExecutionException = catchThrowableOfType(() -> context.completableFuture.get(), ExecutionException.class);
            assertThat(updateExecutionException).isNotNull();

            Throwable rootCause = ExceptionUtils.getRootCause(updateExecutionException);
            assertThat(rootCause).isInstanceOf(PSQLException.class);

            PSQLException psqlException = (PSQLException) rootCause;
            String sqlState = psqlException.getSQLState();
            // This is the exact SQLSTATE code that signalizes an error thrown to prevent serialization anomalies.
            // See: https://www.postgresql.org/docs/15/mvcc-serialization-failure-handling.html
            assertThat(sqlState).isEqualTo("40001");
        };

        TwoThreadsWithTransactions<CompletableFutureContext> twoThreadsWithTransactions = TwoThreadsWithTransactions.configure(entityManagerFactory,
                        CompletableFutureContext::new)
                .threadOneStartsWith(PostgresOptimisticLockingTests::increaseProgrammersSalary)
                .thenThreadTwo(runAsynchronousUpdateOfProgrammersDepartment)
                .thenThreadOne(PostgresOptimisticLockingTests::commitTransaction)
                .thenThreadTwo(assertThatUpdateFailedOnConcurrentModificationException)
                .build();

        twoThreadsWithTransactions.run();
    }

    @Test
    void postgresDoesNotDetectSimultaneousChangesInReadCommittedIsolationLevel() {
        Work updateProgrammersEmailWithRepeatableReadIsolationLevel = conn -> PostgresOptimisticLockingTests.updateProgrammerEmails(conn,
                TRANSACTION_READ_COMMITTED);

        SessionRunnableWithContext<CompletableFutureContext> runAsynchronousUpdateOfProgrammersDepartment = (session, context) -> {
            // We need to do this in another thread and check the result afterwards. For details, see the test above.
            context.completableFuture = CompletableFuture.runAsync(() -> session.doWork(updateProgrammersEmailWithRepeatableReadIsolationLevel),
                    Executors.newSingleThreadExecutor());
        };

        SessionRunnableWithContext<CompletableFutureContext> assertThatUpdateFinishedSuccessfully = (session, context) -> {
            ExecutionException updateExecutionException = catchThrowableOfType(() -> context.completableFuture.get(), ExecutionException.class);
            // Completed successfully, updated rows that were outdated, PostgreSQL did not prevent this.
            assertThat(updateExecutionException).isNull();
        };

        TwoThreadsWithTransactions.configure(entityManagerFactory,
                        CompletableFutureContext::new)
                .threadOneStartsWith(PostgresOptimisticLockingTests::increaseProgrammersSalary)
                .thenThreadTwo(runAsynchronousUpdateOfProgrammersDepartment)
                .thenThreadOneCommits()
                .thenThreadTwo(assertThatUpdateFinishedSuccessfully)
                .run();
    }

    private static void updateProgrammerEmails(Connection connection, int isolationLevel) {
        try {
            connection.setTransactionIsolation(isolationLevel);
            try (PreparedStatement preparedStatement = connection.prepareStatement("" +
                    "UPDATE employees " +
                    "SET email = CONCAT('x', email) " +
                    "WHERE job_id = ?")) {
                preparedStatement.setString(1, IT_PROG.name());
                preparedStatement.executeUpdate();
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static <T> void increaseProgrammersSalary(Session session, T context) {
        int updated = session.createQuery("" +
                        "UPDATE Employee e " +
                        "SET e.salary = e.salary + 1000" +
                        "WHERE e.jobId = :jobId")
                .setParameter("jobId", IT_PROG)
                .executeUpdate();
        assertThat(updated).isNotZero();
    }

    private static <T> void commitTransaction(Session session, T context) {
        session.getTransaction().commit();
    }

    private static class CompletableFutureContext {
        CompletableFuture<Void> completableFuture;
    }
}
