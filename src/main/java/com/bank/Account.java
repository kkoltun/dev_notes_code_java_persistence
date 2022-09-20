package com.bank;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity()
@Table(name = "account")
public class Account {
  @Id
  private String iban;
  private String owner;
  private Integer balance;

  public Account() {
  }

  public Account(String iban, String owner, Integer balance) {
    this.iban = iban;
    this.owner = owner;
    this.balance = balance;
  }

  public String getIban() {
    return iban;
  }

  public void setIban(String iban) {
    this.iban = iban;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public Integer getBalance() {
    return balance;
  }

  public void setBalance(Integer balance) {
    this.balance = balance;
  }
}
