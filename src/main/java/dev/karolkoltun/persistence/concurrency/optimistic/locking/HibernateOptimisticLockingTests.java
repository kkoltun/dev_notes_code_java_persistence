package dev.karolkoltun.persistence.concurrency.optimistic.locking;

import dev.karolkoltun.persistence.concurrency.PostgresHrTest;
import dev.karolkoltun.persistence.entity.EmployeeVersioned;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class HibernateOptimisticLockingTests extends PostgresHrTest {
    @Test
    void simplePresentationTest() {
        // Check logs to see how Hibernate is doing versioning
        doInHibernate(session -> {
            EmployeeVersioned employee = session.find(EmployeeVersioned.class, 100);
            assertThat(employee).isNotNull();

            employee.setSalary(employee.getSalary().add(BigDecimal.TEN));

            // See logs from saving the changes in the DB - there should be:
            // update employees set [columns] where employee_id=? and version=?
        });
    }
}
