package dev.karolkoltun.persistence;

import com.mysql.cj.jdbc.MysqlDataSource;
import dev.karolkoltun.persistence.entity.Account;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;

public class MySqlDataSourceProvider implements DataSourceProvider {
  @Override
  public String hibernateDialect() {
    return "org.hibernate.dialect.MySQL8Dialect";
  }

  @Override
  public DataSource dataSource() {
    MysqlDataSource dataSource = new MysqlDataSource();

    dataSource.setURL(url());
    dataSource.setUser(user());
    dataSource.setPassword(password());

    return dataSource;
  }

  @Override
  public String url() {
    return "jdbc:mysql://localhost/bank?serverTimezone=UTC";
  }

  @Override
  public String user() {
    return "bank";
  }

  @Override
  public String password() {
    return "password";
  }

  @Override
  public List<Class<?>> annotatedClasses() {
    return Collections.singletonList(Account.class);
  }
}
