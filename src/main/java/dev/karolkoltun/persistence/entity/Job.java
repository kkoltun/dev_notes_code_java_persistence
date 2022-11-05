package dev.karolkoltun.persistence.entity;

import java.math.BigDecimal;

public class Job {
  private JobId jobId;
  private String jobTitle;
  private BigDecimal minSalary;
  private BigDecimal maxSalary;

  public Job() {
  }

  public Job(JobId jobId, String jobTitle, BigDecimal minSalary, BigDecimal maxSalary) {
    this.jobId = jobId;
    this.jobTitle = jobTitle;
    this.minSalary = minSalary;
    this.maxSalary = maxSalary;
  }

  public JobId getJobId() {
    return jobId;
  }

  public void setJobId(JobId jobId) {
    this.jobId = jobId;
  }

  public String getJobTitle() {
    return jobTitle;
  }

  public void setJobTitle(String jobTitle) {
    this.jobTitle = jobTitle;
  }

  public BigDecimal getMinSalary() {
    return minSalary;
  }

  public void setMinSalary(BigDecimal minSalary) {
    this.minSalary = minSalary;
  }

  public BigDecimal getMaxSalary() {
    return maxSalary;
  }

  public void setMaxSalary(BigDecimal maxSalary) {
    this.maxSalary = maxSalary;
  }
}
