package dev.karolkoltun.persistence.jdbc;

import dev.karolkoltun.persistence.entity.Employee;
import dev.karolkoltun.persistence.entity.JobId;

import java.sql.ResultSet;
import java.sql.SQLException;

class QueryUtils {
  static Employee employeeFromResultSet(ResultSet resultSet) throws SQLException {
    return new Employee(
        resultSet.getInt("employee_id"),
        resultSet.getString("first_name"),
        resultSet.getString("last_name"),
        resultSet.getString("email"),
        resultSet.getString("phone_number"),
        resultSet.getDate("hire_date").toLocalDate(),
        JobId.valueOf(resultSet.getString("job_id")),
        resultSet.getBigDecimal("salary"));
  }
}
