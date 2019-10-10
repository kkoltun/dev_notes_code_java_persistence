package com.hr.jpa;

import com.hr.Employee;
import com.hr.JobId;

import java.math.BigDecimal;
import java.sql.Date;

import static java.time.LocalDate.now;

class TestUtils {
  static Employee exampleEmployee() {
    return exampleEmployee(1, "Adam", "Kowalski");
  }

  static Employee exampleEmployee(Integer id, String firstName, String lastName) {
    Employee employee = new Employee();

    employee.setId(id);
    employee.setFirstName(firstName);
    employee.setLastName(lastName);
    employee.setEmail("akowalski");
    employee.setHireDate(Date.valueOf(now()));
    employee.setJobId(JobId.FI_ACCOUNT);
    employee.setPhone("1234");
    employee.setSalary(BigDecimal.valueOf(12345));

    return employee;
  }
}
