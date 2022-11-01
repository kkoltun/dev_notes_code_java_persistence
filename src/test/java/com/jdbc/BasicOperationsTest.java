package com.jdbc;

import com.hr.Employee;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.jdbc.QueryUtils.employeeFromResultSet;
import static java.lang.String.format;
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicOperationsTest extends JdbcTest {
  // Sql
  private static final String getByIdSql = "SELECT * FROM employees WHERE employee_id = '%d'";
  private static final String getByFirstNameSql = "SELECT * FROM employees WHERE first_name = ?";

  @Test
  void shouldFindEmployeeByIdUsingPlainStatement() throws Exception {
    // GIVEN
    int id = 100;
    String expectedFirstName = "Steven";
    String expectedLastName = "King";

    // WHEN
    Optional<Employee> employee;
    try (Connection connection = getConnection();
        Statement statement = connection.createStatement()) {
      ResultSet resultSet = statement.executeQuery(format(getByIdSql, id));

      employee = resultSet.next() ? Optional.of(employeeFromResultSet(resultSet)) : empty();
    }

    // THEN
    assertThat(employee).isPresent();
    assertThat(employee.get().getFirstName()).isEqualTo(expectedFirstName);
    assertThat(employee.get().getLastName()).isEqualTo(expectedLastName);
  }

  @Test
  void findEmployeesByFirstNameUsingPreparedStatement() throws Exception {
    // GIVEN
    String firstNameToLookFor = "David";
    int expectedResultSetLength = 3;

    // WHEN
    Set<Employee> employees = new HashSet<>();
    try (PreparedStatement getByFirstName = getConnection().prepareStatement(getByFirstNameSql)) {
      getByFirstName.setString(1, firstNameToLookFor);
      ResultSet resultSet = getByFirstName.executeQuery();

      while (resultSet.next()) {
        employees.add(employeeFromResultSet(resultSet));
      }

      resultSet.close();
    }

    // THEN
    assertEquals(expectedResultSetLength, employees.size());
  }
}
