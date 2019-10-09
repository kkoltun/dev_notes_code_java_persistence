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

  public Employee() {
  }

  public Employee(Integer id, String firstName, String lastName, String email, String phone, Date hireDate, JobId jobId, BigDecimal salary) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.phone = phone;
    this.hireDate = hireDate;
    this.jobId = jobId;
    this.salary = salary;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public Date getHireDate() {
    return hireDate;
  }

  public void setHireDate(Date hireDate) {
    this.hireDate = hireDate;
  }

  public JobId getJobId() {
    return jobId;
  }

  public void setJobId(JobId jobId) {
    this.jobId = jobId;
  }

  public BigDecimal getSalary() {
    return salary;
  }

  public void setSalary(BigDecimal salary) {
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
