package com.concurrency;

import com.hr.Employee;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;

public class PostgresqlHrDataSourceProvider implements DataSourceProvider {
    @Override
    public String hibernateDialect() {
        return "org.hibernate.dialect.PostgreSQLDialect";
    }

    @Override
    public DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();

        dataSource.setURL(url());
        dataSource.setUser(user());
        dataSource.setPassword(password());

        return dataSource;
    }

    @Override
    public String url() {
        return "jdbc:postgresql://localhost:15432/hr";
    }

    @Override
    public String user() {
        return "hr";
    }

    @Override
    public String password() {
        return "password";
    }

    @Override
    public List<Class<?>> annotatedClasses() {
        return Collections.singletonList(Employee.class);
    }
}
