package dev.karolkoltun.persistence.jpa;

import dev.karolkoltun.persistence.entity.Employee;
import dev.karolkoltun.persistence.entity.JobId;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    employee.setHireDate(LocalDate.now());
    employee.setJobId(JobId.FI_ACCOUNT);
    employee.setPhone("1234");
    employee.setSalary(BigDecimal.valueOf(12345));

    return employee;
  }
}
