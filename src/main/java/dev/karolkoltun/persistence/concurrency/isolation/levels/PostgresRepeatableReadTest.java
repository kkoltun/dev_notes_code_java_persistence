package dev.karolkoltun.persistence.concurrency.isolation.levels;

import dev.karolkoltun.persistence.concurrency.EmployeeContext;
import dev.karolkoltun.persistence.concurrency.EmptyContext;
import dev.karolkoltun.persistence.concurrency.SessionRunnableWithContext;
import dev.karolkoltun.persistence.concurrency.TwoThreadsWithTransactions;
import dev.karolkoltun.persistence.entity.Employee;
import org.hibernate.FlushMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static java.sql.Connection.TRANSACTION_SERIALIZABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.FlushMode.AUTO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PostgresRepeatableReadTest extends PostgresIsolationLevelTest {

    @Override
    int getTestedIsolationLevel() {
        return TRANSACTION_SERIALIZABLE;
    }

    @Test
    void dirtyReadIsNotPossible_unrepeatableReadIsNotPossible() {
        BigDecimal salaryBefore = getUsingHibernateReadOnly(session -> session.find(Employee.class, 100).getSalary());

        TwoThreadsWithTransactions.configure(entityManagerFactory, EmptyContext::new)
                .threadOneStartsWith((session, context) -> increaseEmployeeSalary(session, 100))
                // If this fails, then dirty read is happening.
                .thenThreadTwo((session, context) -> assertSelectedSalary(session, 100, salaryBefore))
                .thenThreadOneCommits()
                // If this fails, then unrepeatable read is happening.
                .thenThreadTwo((session, context) -> assertSelectedSalary(session, 100, salaryBefore))
                .run();
    }

    // TODO Why does this fail?
    // T1 SELECT should acquire a shared lock on the row.
    // T2 DELETE should not be able to acquire an exclusive lock on the deleted row.
    @Test
    void selectBlocksDelete() {
        int id = addExampleEmployee();

        SessionRunnableWithContext<EmployeeContext> selectEmployee = (session, context) -> {
            Employee employee = session.find(Employee.class, id);
            assertNotNull(employee);
            context.setEmployee(employee);
        };

        SessionRunnableWithContext<EmployeeContext> deleteEmployee = (session, context) -> {
            int deletedCount = session.createQuery("DELETE FROM Employee WHERE id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            assertThat(deletedCount).isEqualTo(1);
            session.getTransaction().commit();
        };

        TwoThreadsWithTransactions.configure(entityManagerFactory, EmployeeContext::new)
                .threadOneStartsWith(selectEmployee)
                .thenThreadTwoTimeoutsOn(deleteEmployee, Duration.ofSeconds(3))
                .run();
    }
}
