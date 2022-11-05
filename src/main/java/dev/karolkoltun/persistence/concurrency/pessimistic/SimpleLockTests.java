package dev.karolkoltun.persistence.concurrency.pessimistic;

import dev.karolkoltun.persistence.DataSourceProvider;
import dev.karolkoltun.persistence.entity.Employee;
import dev.karolkoltun.persistence.HibernateTest;
import dev.karolkoltun.persistence.PostgresqlHrDataSourceProvider;
import dev.karolkoltun.persistence.concurrency.EmployeeContext;
import dev.karolkoltun.persistence.concurrency.SessionRunnableWithContext;
import dev.karolkoltun.persistence.concurrency.TwoThreadsWithTransactions;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.LockModeType;
import javax.persistence.LockTimeoutException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleLockTests extends HibernateTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleLockTests.class);

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
            context.setEmployee(employee);
        };
        SessionRunnableWithContext<EmployeeContext> checkStep = (session, context) -> {
            assertNotNull(context.getEmployee());
            assertTrue(session.isOpen());
            assertTrue(session.contains(context.getEmployee()));
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
            context.setEmployee(employee);
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
}
