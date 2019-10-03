package com.hr;

import java.math.BigDecimal;

class Job {
  private JobId jobId;
  private String jobTitle;
  private BigDecimal minSalary;
  private BigDecimal maxSalary;

  Job(JobId jobId, String jobTitle, BigDecimal minSalary, BigDecimal maxSalary) {
    this.jobId = jobId;
    this.jobTitle = jobTitle;
    this.minSalary = minSalary;
    this.maxSalary = maxSalary;
  }

  JobId getJobId() {
    return jobId;
  }

  String getJobTitle() {
    return jobTitle;
  }

  BigDecimal getMinSalary() {
    return minSalary;
  }

  BigDecimal getMaxSalary() {
    return maxSalary;
  }
}
