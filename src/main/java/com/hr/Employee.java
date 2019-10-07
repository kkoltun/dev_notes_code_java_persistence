package com.hr;

import javax.persistence.*;
import java.math.BigDecimal;
import java.sql.Date;

import static javax.persistence.EnumType.STRING;

@Entity
@Table(name = "employees")
public class Employee {

  @Id
//  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "employee_id")
  private Integer id;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  private String email;

  @Column(name = "phone_number")
  private String phone;

  @Column(name = "hire_date")
  private Date hireDate;

  @Column(name = "job_id")
  @Enumerated(STRING)
  private JobId jobId;

  private BigDecimal salary;

  Employee() {}

  Employee(
      Integer id,
      String firstName,
      String lastName,
      String email,
      String phone,
      Date hireDate,
      JobId jobId,
      BigDecimal salary) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.phone = phone;
    this.hireDate = hireDate;
    this.jobId = jobId;
    this.salary = salary;
  }

  Integer getId() {
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

  void setId(Integer id) {
    this.id = id;
  }

  void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  void setLastName(String lastName) {
    this.lastName = lastName;
  }

  void setEmail(String email) {
    this.email = email;
  }

  void setPhone(String phone) {
    this.phone = phone;
  }

  void setHireDate(Date hireDate) {
    this.hireDate = hireDate;
  }

  void setJobId(JobId jobId) {
    this.jobId = jobId;
  }

  void setSalary(BigDecimal salary) {
    this.salary = salary;
  }

  @Override
  public String toString() {
    return "Employee{"
        + "id="
        + id
        + ", firstName='"
        + firstName
        + '\''
        + ", lastName='"
        + lastName
        + '\''
        + ", email='"
        + email
        + '\''
        + ", phone='"
        + phone
        + '\''
        + ", hireDate="
        + hireDate
        + ", jobId="
        + jobId
        + ", salary="
        + salary
        + '}';
  }
}
