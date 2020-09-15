package com.hr.jdbc;

import com.hr.Employee;
import com.hr.JobId;

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
        resultSet.getDate("hire_date"),
        JobId.valueOf(resultSet.getString("job_id")),
        resultSet.getBigDecimal("salary"));
  }
}
