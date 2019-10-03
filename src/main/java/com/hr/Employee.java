package com.hr;

import java.math.BigDecimal;
import java.sql.Date;

public class Employee {
  private int id;
  private String firstName;
  private String lastName;
  private String email;
  // todo String?
  private String phone;
  private Date hireDate;
  private JobId jobId;
  private BigDecimal salary;

  Employee(int id, String firstName, String lastName, String email, String phone, Date hireDate, JobId jobId, BigDecimal salary) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.phone = phone;
    this.hireDate = hireDate;
    this.jobId = jobId;
    this.salary = salary;
  }

  int getId() {
    return id;
  }

  String getFirstName() {
    return firstName;
  }

  String getLastName() {
    return lastName;
  }

  String getEmail() {
    return email;
  }

  String getPhone() {
    return phone;
  }

  Date getHireDate() {
    return hireDate;
  }

  JobId getJobId() {
    return jobId;
  }

  BigDecimal getSalary() {
    return salary;
  }

  @Override
  public String toString() {
    return "Employee{" +
        "id=" + id +
        ", firstName='" + firstName + '\'' +
        ", lastName='" + lastName + '\'' +
        ", email='" + email + '\'' +
        ", phone='" + phone + '\'' +
        ", hireDate=" + hireDate +
        ", jobId=" + jobId +
        ", salary=" + salary +
        '}';
  }
}
