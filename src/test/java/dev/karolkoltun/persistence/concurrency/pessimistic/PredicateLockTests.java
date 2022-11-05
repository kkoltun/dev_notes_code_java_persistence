package dev.karolkoltun.persistence.concurrency.pessimistic;

import dev.karolkoltun.persistence.*;
import dev.karolkoltun.persistence.concurrency.EmptyContext;
import dev.karolkoltun.persistence.concurrency.SessionRunnableWithContext;
import dev.karolkoltun.persistence.concurrency.TwoThreadsWithTransactions;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;

import javax.persistence.LockModeType;
import javax.persistence.LockTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static dev.karolkoltun.persistence.JobId.FI_ACCOUNT;
import static dev.karolkoltun.persistence.JobId.IT_PROG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PredicateLockTests extends HibernateTest {

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
    void predicateLockingExample() {
        // Check logs to see the SQL statement used by Hibernate.
        doInHibernateReadOnly((Session session) -> session.createQuery("" +
                        "SELECT e " +
                        "FROM Employee e " +
                        "WHERE e.jobId = 'SA_REP'", Employee.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList());
    }

    @Test
    void predicateLockingNoWaitExample() {
        // Check logs to see the SQL statement used by Hibernate.
        LockOptions lockOptions = new LockOptions(LockMode.PESSIMISTIC_WRITE);
        lockOptions.setTimeOut(LockOptions.NO_WAIT);

        // Check logs to see the SQL statement used by Hibernate.
        doInHibernateReadOnly(session -> session.find(Employee.class, 1,
                LockModeType.PESSIMISTIC_WRITE,
                Collections.singletonMap(
                        AvailableSettings.JPA_LOCK_TIMEOUT,
                        LockOptions.NO_WAIT
                )
        ));
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

        SessionRunnableWithContext<EmptyContext> getAccountantsWithLock = (session, context) -> assertThrows(LockTimeoutException.class, () -> getEmployeesByJobId.apply(session, IT_PROG), "Caught LockTimeoutException exception.");

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
    void predicateLockBlocksUpdateInTheSameRange() throws Throwable {
        LockOptions pessimisticNoWaitLock = new LockOptions();
        pessimisticNoWaitLock.setLockMode(LockMode.PESSIMISTIC_WRITE);
        pessimisticNoWaitLock.setTimeOut(LockOptions.NO_WAIT);

        SessionRunnableWithContext<EmptyContext> getProgrammersWithLock = (session, context) -> {
            List<Employee> programmers = session.createQuery("" +
                            "SELECT e " +
                            "FROM Employee e " +
                            "WHERE e.jobId = :jobId", Employee.class)
                    .setLockOptions(pessimisticNoWaitLock)
                    .setParameter("jobId", IT_PROG)
                    .getResultList();
            assertThat(programmers).isNotEmpty();
        };

        SessionRunnableWithContext<EmptyContext> updateProgrammers = (session, context) -> {
            int updated = session.createQuery("" +
                            "UPDATE Employee e " +
                            "SET e.salary = 1.5 * e.salary " +
                            "WHERE e.jobId = :jobId")
                    .setParameter("jobId", IT_PROG)
                    .executeUpdate();
            assertThat(updated).isNotZero();
        };

        TwoThreadsWithTransactions<EmptyContext> twoThreadsWithTransactions = TwoThreadsWithTransactions.configure(entityManagerFactory, EmptyContext::new)
                .threadOneStartsWith(getProgrammersWithLock)
                .thenThreadTwoTimeoutsOn(updateProgrammers, Duration.ofSeconds(5))
                .thenFinish();

        twoThreadsWithTransactions.run();
    }

    @Test
    void predicateLockDoesNotBlockUpdateInDifferentSameRange() throws Throwable {
        LockOptions pessimisticNoWaitLock = new LockOptions();
        pessimisticNoWaitLock.setLockMode(LockMode.PESSIMISTIC_WRITE);
        pessimisticNoWaitLock.setTimeOut(LockOptions.NO_WAIT);

        SessionRunnableWithContext<EmptyContext> getProgrammersWithLock = (session, context) -> {
            List<Employee> programmers = session.createQuery("" +
                            "SELECT e " +
                            "FROM Employee e " +
                            "WHERE e.jobId = :jobId", Employee.class)
                    .setLockOptions(pessimisticNoWaitLock)
                    .setParameter("jobId", IT_PROG)
                    .getResultList();
            assertThat(programmers).isNotEmpty();
        };

        SessionRunnableWithContext<EmptyContext> updateAccountants = (session, context) -> {
            int updated = session.createQuery("" +
                            "UPDATE Employee e " +
                            "SET e.salary = 1.5 * e.salary " +
                            "WHERE e.jobId = :jobId")
                    .setParameter("jobId", FI_ACCOUNT)
                    .executeUpdate();
            assertThat(updated).isNotZero();
        };

        TwoThreadsWithTransactions<EmptyContext> twoThreadsWithTransactions = TwoThreadsWithTransactions.configure(entityManagerFactory, EmptyContext::new)
                .threadOneStartsWith(getProgrammersWithLock)
                .thenThreadTwo(updateAccountants)
                .thenFinish();

        twoThreadsWithTransactions.run();
    }

    // This is an interesting case in Postgres
    @Test
    void predicateLockDoesNotBlockInsertInTheSameRange() throws Throwable {
        LockOptions pessimisticNoWaitLock = new LockOptions();
        pessimisticNoWaitLock.setLockMode(LockMode.PESSIMISTIC_WRITE);
        pessimisticNoWaitLock.setTimeOut(LockOptions.NO_WAIT);

        Function<Session, List<Employee>> getAllProgrammersWithLock = session -> session.createQuery("" +
                        "SELECT e " +
                        "FROM Employee e " +
                        "WHERE e.jobId = :jobId", Employee.class)
                .setLockOptions(pessimisticNoWaitLock)
                .setParameter("jobId", IT_PROG)
                .getResultList();

        SessionRunnableWithContext<EmptyContext> getProgrammersWithLock = (session, context) -> {
            List<Employee> programmersBefore = getAllProgrammersWithLock.apply(session);
            assertThat(programmersBefore).isNotEmpty();
        };

        SessionRunnableWithContext<EmptyContext> addNewProgrammer = (session, context) -> {
            int availableId = session.createQuery("SELECT MAX(e.id) FROM Employee e", Integer.class)
                    .getSingleResult();
            int updated = session.createSQLQuery("" +
                            "INSERT INTO employees (employee_id, first_name, last_name, email, phone_number, hire_date, job_id, salary, commission_pct, manager_id, department_id) " +
                            "VALUES (:id, 'New', 'Employee', :mail, '1245', :date, :jobId, 1234, NULL, NULL, 90)")
                    .setParameter("id", availableId + 1)
                    .setParameter("mail", "emp" + (availableId + 1) + "@hr.co")
                    .setParameter("date", LocalDate.now())
                    .setParameter("jobId", IT_PROG.name())
                    .executeUpdate();
            session.flush();
            assertThat(updated).isNotZero();
        };

        TwoThreadsWithTransactions<EmptyContext> twoThreadsWithTransactions = TwoThreadsWithTransactions.configure(entityManagerFactory, EmptyContext::new)
                .threadOneStartsWith(getProgrammersWithLock)
                .thenThreadTwo(addNewProgrammer)
                .thenFinish();

        twoThreadsWithTransactions.run();
    }

    // todo tests:
    //  1. predicate lock -> blocks update in the same range
    //  2. predicate lock -> blocks delete in the same range
    //  3. predicate lock -> does not block insert in the same range
    //  4. predicate lock -> does not block update in another range
    //  5. predicate lock -> does not block delete in another range

}
