package com.hr.hibernate;

import com.hr.Employee;
import com.hr.JobId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicOperationsTest extends HibernateTest {
    @Test
    void shouldGetEmployee() {
        // WHEN
        List<Employee> employees = session.createQuery("" +
                        "SELECT emp " +
                        "FROM Employee emp " +
                        "WHERE emp.firstName = :firstName" +
                        "  AND emp.lastName = :lastName ", Employee.class)
                .setParameter("firstName", "Lex")
                .setParameter("lastName", "De Haan")
                .list();

        // THEN
        assertThat(employees).hasSize(1);
    }

    @Test
    void namedQueryShouldGetEmployee() {
        // WHEN
        List<Employee> employees = session.createNamedQuery("get_employee_by_first_name_and_last_name", Employee.class)
                .setParameter("firstName", "Lex")
                .setParameter("lastName", "De Haan")
                .list();

        // THEN
        assertThat(employees).hasSize(1);
    }
}
