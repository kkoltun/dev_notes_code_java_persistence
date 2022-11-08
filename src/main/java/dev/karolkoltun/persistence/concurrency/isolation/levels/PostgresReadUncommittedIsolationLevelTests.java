package dev.karolkoltun.persistence.concurrency.isolation.levels;

import dev.karolkoltun.persistence.concurrency.EmptyContext;
import dev.karolkoltun.persistence.concurrency.TwoThreadsWithTransactions;
import dev.karolkoltun.persistence.entity.Employee;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
import static org.assertj.core.api.Assertions.assertThat;

public class PostgresReadUncommittedIsolationLevelTests extends PostgresIsolationLevelTest {

    @Override
    int getTestedIsolationLevel() {
        return TRANSACTION_READ_UNCOMMITTED;
    }

    // Two issues checked in one test, because it is just easier.
    @Test
    void dirtyReadIsNotPossible_unrepeatableReadIsPossible() {
        BigDecimal salaryBefore = getUsingHibernateReadOnly(session -> session.find(Employee.class, 100).getSalary());
        BigDecimal salaryAfter = salaryBefore.add(BigDecimal.valueOf(100));

        TwoThreadsWithTransactions.configure(entityManagerFactory, EmptyContext::new)
                .threadOneStartsWith((session, context) -> increaseEmployeeSalary(session, 100))
                // If this fails, then dirty read is happening. It is not possible in PostgreSQL.
                .thenThreadTwo((session, context) -> assertSelectedSalary(session, 100, salaryBefore))
                .thenThreadOneCommits()
                // This is an unrepeatable read.
                .thenThreadTwo((session, context) -> assertSelectedSalary(session, 100, salaryAfter))
                .run();
    }


}
