package com.concurrency;

import com.hr.Employee;
import com.hr.JobId;
import org.hibernate.FlushMode;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.LockModeType;
import javax.persistence.LockTimeoutException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hr.JobId.FI_ACCOUNT;
import static com.hr.JobId.IT_PROG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class PessimisticLockingExamples extends HibernateTest {

    private static final Logger log = LoggerFactory.getLogger(PessimisticLockingExamples.class);

    @Override
    DataSourceProvider dataSourceProvider() {
        return new PostgresqlHrDataSourceProvider();
    }

    @Override
    boolean recreateBeforeEachTest() {
        // Reuse the data that came with the database.
        return false;
    }

    @Test
    void pessimisticReadExample() {
        // Check logs to see the SQL statement used by Hibernate.
        doInHibernate(session -> {
            Employee employee = session.get(Employee.class, 100);

            session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_READ))
                    .lock(employee);

            log.info("Check logs to see the locking SQL expression.");
        });
    }

    @Test
    void pessimisticWriteExample() {
        // Check logs to see the SQL statement used by Hibernate.
        doInHibernate(session -> {
            Employee employee = session.get(Employee.class, 100);

            session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_WRITE))
                    .lock(employee);
        });
    }

    @Test
    void predicateLockingExample() {
        // Check logs to see the SQL statement used by Hibernate.
        doInHibernate(session -> {
            session.createQuery("" +
                            "SELECT e " +
                            "FROM Employee e " +
                            "WHERE e.jobId = 'SA_REP'", Employee.class)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultList();
        });
    }

    @Test
    void predicateLockingNoWaitExample() {
        // Check logs to see the SQL statement used by Hibernate.
        LockOptions lockOptions = new LockOptions(LockMode.PESSIMISTIC_WRITE);
        lockOptions.setTimeOut(LockOptions.NO_WAIT);

        // Check logs to see the SQL statement used by Hibernate.
        doInHibernate(session -> {
            session.find(Employee.class, 1,
                    LockModeType.PESSIMISTIC_WRITE,
                    Collections.singletonMap(
                            AvailableSettings.JPA_LOCK_TIMEOUT,
                            LockOptions.NO_WAIT
                    )
            );
        });
    }

    @Test
    void sharedLockAllowsOtherSharedLock() throws Throwable {
        SessionRunnableWithContext<EmployeeContext> getEmployeeOneWithSharedLockStep = (session, context) -> {
            // Acquire shared lock on Employee #100; use NOWAIT to detect any conflicts right away.
            Employee employee = session.find(Employee.class, 100,
                    LockModeType.PESSIMISTIC_READ,
                    Collections.singletonMap(
                            AvailableSettings.JPA_LOCK_TIMEOUT,
                            LockOptions.NO_WAIT
                    ));
            assertNotNull(employee);
            context.employee = employee;
        };
        SessionRunnableWithContext<EmployeeContext> checkStep = (session, context) -> {
            assertNotNull(context.employee);
            assertTrue(session.isOpen());
            assertTrue(session.contains(context.employee));
        };

        TwoThreadsWithTransactions<EmployeeContext> twoThreadsWithTransactions = TwoThreadsWithTransactions.configure(entityManagerFactory,
                        EmployeeContext::new)
                .threadOneStartsWith(getEmployeeOneWithSharedLockStep)
                .thenThreadTwo(getEmployeeOneWithSharedLockStep)
                .thenThreadOne(checkStep)
                .thenThreadTwo(checkStep)
                .thenFinish();

        twoThreadsWithTransactions.run();
    }

    @Test
    void sharedLockDoesNotAllowsOtherExclusiveLock() throws Throwable {
        SessionRunnableWithContext<EmployeeContext> getEmployeeOneWithSharedLock = (session, context) -> {
            // Acquire shared lock on Employee #100; use NOWAIT to detect any conflicts right away.
            Employee employee = session.find(Employee.class, 100,
                    LockModeType.PESSIMISTIC_READ,
                    Collections.singletonMap(
                            AvailableSettings.JPA_LOCK_TIMEOUT,
                            LockOptions.NO_WAIT
                    ));
            assertNotNull(employee);
            context.employee = employee;
        };

        SessionRunnableWithContext<EmployeeContext> failAtGettingExclusiveLock = (session, context) -> {
            // Acquire an exclusive lock on Employee #100; use NOWAIT to detect any conflicts right away.
            Executable getExclusiveLock = () -> session.find(Employee.class, 100,
                    LockModeType.PESSIMISTIC_WRITE,
                    Collections.singletonMap(
                            AvailableSettings.JPA_LOCK_TIMEOUT,
                            LockOptions.NO_WAIT
                    ));
            assertThrows(LockTimeoutException.class, getExclusiveLock);
        };

        TwoThreadsWithTransactions<EmployeeContext> twoThreadsWithTransactions = TwoThreadsWithTransactions.configure(entityManagerFactory,
                        EmployeeContext::new)
                .threadOneStartsWith(getEmployeeOneWithSharedLock)
                .thenThreadTwo(failAtGettingExclusiveLock)
                .thenFinish();

        twoThreadsWithTransactions.run();
    }

    @Test
    void predicateLockDoesNotAllowsOtherPredicateLockInTheSameRange() throws Throwable {
        LockOptions pessimisticNoWaitLock = new LockOptions();
        pessimisticNoWaitLock.setLockMode(LockMode.PESSIMISTIC_WRITE);
        pessimisticNoWaitLock.setTimeOut(LockOptions.NO_WAIT);

        // Acquire lock, use NOWAIT to detect any conflicts right away.
        BiFunction<Session, JobId, List<Employee>> getEmployeesByJobId = (session, jobId) -> session.createQuery("" +
                        "SELECT e " +
                        "FROM Employee e " +
                        "WHERE e.jobId = :jobId", Employee.class)
                .setLockOptions(pessimisticNoWaitLock)
                .setParameter("jobId", jobId)
                .getResultList();

        SessionRunnableWithContext<EmptyContext> getProgrammersWithLock = (session, context) -> {
            List<Employee> programmers = getEmployeesByJobId.apply(session, IT_PROG);
            assertThat(programmers).isNotEmpty();
        };

        SessionRunnableWithContext<EmptyContext> getAccountantsWithLock = (session, context) -> {
            assertThrows(LockTimeoutException.class, () -> getEmployeesByJobId.apply(session, IT_PROG), "Caught LockTimeoutException exception.");
        };

        TwoThreadsWithTransactions<EmptyContext> twoThreadsWithTransactions = TwoThreadsWithTransactions.configure(entityManagerFactory, EmptyContext::new)
                .threadOneStartsWith(getProgrammersWithLock)
                .thenThreadTwo(getAccountantsWithLock)
                .thenFinish();

        twoThreadsWithTransactions.run();
    }

    @Test
    void predicateLockAllowsAnotherLockOnDifferentRange() throws Throwable {
        LockOptions pessimisticNoWaitLock = new LockOptions();
        pessimisticNoWaitLock.setLockMode(LockMode.PESSIMISTIC_WRITE);
        pessimisticNoWaitLock.setTimeOut(LockOptions.NO_WAIT);

        // Acquire lock, use NOWAIT to detect any conflicts right away.
        BiFunction<Session, JobId, List<Employee>> getEmployeesByJobId = (session, jobId) -> session.createQuery("" +
                        "SELECT e " +
                        "FROM Employee e " +
                        "WHERE e.jobId = :jobId", Employee.class)
                .setLockOptions(pessimisticNoWaitLock)
                .setParameter("jobId", jobId)
                .getResultList();

        SessionRunnableWithContext<EmptyContext> getProgrammersWithLock = (session, context) -> {
            List<Employee> programmers = getEmployeesByJobId.apply(session, IT_PROG);
            assertThat(programmers).isNotEmpty();
        };

        SessionRunnableWithContext<EmptyContext> getAccountantsWithLock = (session, context) -> {
            List<Employee> accountants = getEmployeesByJobId.apply(session, FI_ACCOUNT);
            assertThat(accountants).isNotEmpty();
        };

        TwoThreadsWithTransactions<EmptyContext> twoThreadsWithTransactions = TwoThreadsWithTransactions.configure(entityManagerFactory, EmptyContext::new)
                .threadOneStartsWith(getProgrammersWithLock)
                .thenThreadTwo(getAccountantsWithLock)
                .thenFinish();

        twoThreadsWithTransactions.run();
    }

    @Test
    void predicateLockBlocksUpdate() throws Throwable {
        LockOptions pessimisticNoWaitLock = new LockOptions();
        pessimisticNoWaitLock.setLockMode(LockMode.PESSIMISTIC_WRITE);
        pessimisticNoWaitLock.setTimeOut(LockOptions.NO_WAIT);

        // Acquire lock, use NOWAIT to detect any conflicts right away.
        Function<Session, List<Employee>> getProgrammers = session -> session.createQuery("" +
                        "SELECT e " +
                        "FROM Employee e " +
                        "WHERE e.jobId = :jobId", Employee.class)
                .setLockOptions(pessimisticNoWaitLock)
                .setParameter("jobId", IT_PROG)
                .getResultList();

        Function<Session, Integer> updateProgrammers = session -> session.createQuery("" +
                        "UPDATE Employee e " +
                        "SET e.salary = 1.5 * e.salary " +
                        "WHERE e.jobId = :jobId")
                .setParameter("jobId", IT_PROG)
                .executeUpdate();

        SessionRunnableWithContext<EmptyContext> getProgrammersWithLock = (session, context) -> {
            List<Employee> programmers = getProgrammers.apply(session);
            BigDecimal totalSalary = programmers.stream()
                    .map(Employee::getSalary)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            log.info("Total salary of programmers: " + totalSalary);
            assertThat(programmers).isNotEmpty();
        };

        SessionRunnableWithContext<EmptyContext> getAccountantsWithLock = (session, context) -> {
            int updated = updateProgrammers.apply(session);
            assertThat(updated).isNotZero();
        };

        TwoThreadsWithTransactions<EmptyContext> twoThreadsWithTransactions = TwoThreadsWithTransactions.configure(entityManagerFactory, EmptyContext::new)
                .threadOneStartsWith(getProgrammersWithLock)
                .thenThreadTwoTimeoutsOn(getAccountantsWithLock, Duration.ofSeconds(3))
                .thenFinish();

        twoThreadsWithTransactions.run();
    }

    // todo tests:
    //  1. predicate lock -> blocks update in the same range
    //  2. predicate lock -> blocks delete in the same range
    //  3. predicate lock -> does not block insert in the same range
    //  4. predicate lock -> does not block update in another range
    //  5. predicate lock -> does not block delete in another range

    // In these tests, we need a non-read-only behavior.
    void doInHibernate(Consumer<Session> callable) {
        doInHibernate(callable, false, FlushMode.AUTO);
    }

    private static class EmployeeContext {
        Employee employee;
    }

    private static class EmployeesContext {
        List<Employee> employees;
    }

    private static class EmptyContext {

    }
}
