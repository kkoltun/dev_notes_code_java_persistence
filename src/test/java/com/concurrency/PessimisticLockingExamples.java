package com.concurrency;

import com.hr.Employee;
import org.hibernate.FlushMode;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.LockModeType;
import java.util.Collections;
import java.util.function.Consumer;

public class PessimisticLockingExamples extends HibernateTest {

    private static final Logger log = LoggerFactory.getLogger(ACIDRaceConditionTest.class);

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
        doInHibernate(session -> {
            Employee employee = session.get(Employee.class, 100);

            session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_READ))
                    .lock(employee);

            log.info("Check logs to see the locking SQL expression.");
        });
    }

    @Test
    void pessimisticWriteExample() {
        doInHibernate(session -> {
            Employee employee = session.get(Employee.class, 100);

            session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_WRITE))
                    .lock(employee);

            log.info("Check logs to see the locking SQL expression.");
        });
    }

    @Test
    void predicateLockingExample() {
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
        LockOptions lockOptions = new LockOptions(LockMode.PESSIMISTIC_WRITE);
        lockOptions.setTimeOut(LockOptions.NO_WAIT);

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

    // In these tests, we need a non-read-only behavior.
    void doInHibernate(Consumer<Session> callable) {
        doInHibernate(callable, false, FlushMode.AUTO);
    }
}
