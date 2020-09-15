package com.hr;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollServiceTest {

  private PayrollService payrollService = new PayrollService();

  @Test
  void shouldAllowUpdate() {
    BigDecimal currentSalary = BigDecimal.valueOf(1000);
    BigDecimal allowedUpdate = BigDecimal.valueOf(90);

    Employee employee = new Employee();
    employee.setSalary(currentSalary);

    assertTrue(payrollService.isSalaryUpdateAllowed(employee, allowedUpdate));
  }

  @Test
  void shouldNotAllowTooBigUpdate() {
    BigDecimal currentSalary = BigDecimal.valueOf(1000);
    BigDecimal disallowedUpdate = BigDecimal.valueOf(110);

    Employee employee = new Employee();
    employee.setSalary(currentSalary);

    assertFalse(payrollService.isSalaryUpdateAllowed(employee, disallowedUpdate));
  }

  @Test
  void shouldNotAllowReduction() {
    BigDecimal currentSalary = BigDecimal.valueOf(1000);
    BigDecimal disallowedUpdate = BigDecimal.valueOf(-10);

    Employee employee = new Employee();
    employee.setSalary(currentSalary);

    assertFalse(payrollService.isSalaryUpdateAllowed(employee, disallowedUpdate));
  }
}