package dev.karolkoltun.persistence.jdbc;

import dev.karolkoltun.persistence.entity.Employee;
import dev.karolkoltun.persistence.entity.PayrollService;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionsTest extends JdbcTest {

  private static final Map<Integer, BigDecimal> plannedUpdateSalaries = new HashMap<>();

  static {
    plannedUpdateSalaries.put(100, BigDecimal.valueOf(1000));
    plannedUpdateSalaries.put(101, BigDecimal.valueOf(1000));
    plannedUpdateSalaries.put(102, BigDecimal.valueOf(1000));
    plannedUpdateSalaries.put(103, BigDecimal.valueOf(1000));
  }

  private static final String getByIdString = "SELECT * FROM employees WHERE employee_id = ?";

  private static final String incrementSalaryString = "UPDATE employees " +
      "SET salary = salary + ? WHERE employee_id = ?";

  private final PayrollService payrollService = new PayrollService();

  @Test
  void updateEmployeeSalaries() throws Exception {
    // Given
    Map<Integer, BigDecimal> salariesBeforeUpdate = new HashMap<>();
    plannedUpdateSalaries.keySet().forEach(id -> {
      try {
        Employee  employee = getEmployeeById(id);
        salariesBeforeUpdate.put(employee.getId(), employee.getSalary());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // When
    PreparedStatement updateSalary = null;
    Connection connection = getConnection();

    try {
      connection.setAutoCommit(false);
      updateSalary = connection.prepareStatement(incrementSalaryString);

      for (Map.Entry<Integer, BigDecimal> entry : plannedUpdateSalaries.entrySet()) {
        updateSalary.setBigDecimal(1, entry.getValue());
        updateSalary.setInt(2, entry.getKey());
        updateSalary.executeUpdate();
        connection.commit();
      }
    } catch (SQLException e) {
      e.printStackTrace();

      try {
        System.err.print("Transaction is being rolled back");
        connection.rollback();
      } catch (SQLException excep) {
        e.printStackTrace();
      }
    } finally {
      if (updateSalary != null) {
        updateSalary.close();
      }
      connection.setAutoCommit(true);
    }

    // Then
    salariesBeforeUpdate.forEach((id, oldSalary) -> {
      try {
        BigDecimal update = plannedUpdateSalaries.get(id);
        assertEquals(oldSalary.add(update), getEmployeeById(id).getSalary());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Test
  void rollbackEmployeeSalaryUpdates() throws Exception {
    // Given
    int id = 100;
    BigDecimal salaryUpdate = BigDecimal.valueOf(-10);
    BigDecimal oldSalary = getEmployeeById(id).getSalary();

    // When
    Connection connection = getConnection();

    connection.setAutoCommit(false);

    try (PreparedStatement getById = connection.prepareStatement(getByIdString);
            PreparedStatement updateSalary = connection.prepareStatement(incrementSalaryString)) {
      Savepoint savepoint = connection.setSavepoint();

      // Get the employee
      getById.setInt(1, id);

      ResultSet resultSet = getById.executeQuery();
      Employee employee = resultSet.next() ? QueryUtils.employeeFromResultSet(resultSet) : null;

      // Update the salary

      updateSalary.setBigDecimal(1, salaryUpdate);
      updateSalary.setInt(2, id);
      updateSalary.executeUpdate();

      // Check if it is ok
      if (!payrollService.isSalaryUpdateAllowed(employee, salaryUpdate)) {
        connection.rollback(savepoint);
      }

      connection.commit();
    } catch (SQLException e) {
      e.printStackTrace();

      try {
        System.err.print("Transaction is being rolled back");
        connection.rollback();
      } catch (SQLException excep) {
        e.printStackTrace();
      }
    } finally {
      connection.setAutoCommit(true);
    }

    // Then
    assertEquals(getEmployeeById(id).getSalary(), oldSalary);
  }

  Employee getEmployeeById(int id) throws Exception {
    try(Connection connection = getConnection();
            PreparedStatement getById = connection.prepareStatement(getByIdString)) {
      getById.setInt(1, id);

      ResultSet resultSet = getById.executeQuery();
      return resultSet.next() ? QueryUtils.employeeFromResultSet(resultSet) : null;
    }
  }
}
