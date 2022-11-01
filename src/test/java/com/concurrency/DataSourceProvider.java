package com.concurrency;

import javax.sql.DataSource;
import java.util.List;

public interface DataSourceProvider {
  String hibernateDialect();

  DataSource dataSource();

  String url();

  String user();

  String password();

  List<Class<?>> annotatedClasses();
}
