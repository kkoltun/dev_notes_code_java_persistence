package com.bank;

import javax.sql.DataSource;

public interface DataSourceProvider {
  String hibernateDialect();

  DataSource dataSource();

  String url();

  String user();

  String password();
}
