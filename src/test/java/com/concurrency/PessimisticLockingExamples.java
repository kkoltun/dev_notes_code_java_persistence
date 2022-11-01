package com.concurrency;

import com.hr.Employee;
import org.hibernate.FlushMode;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // In these tests, we need a non-read-only behavior.
    void doInHibernate(Consumer<Session> callable) {
        doInHibernate(callable, false, FlushMode.AUTO);
    }
}
