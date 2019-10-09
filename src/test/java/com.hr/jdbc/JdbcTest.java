package com.hr.jdbc;

import org.h2.tools.Server;
import org.junit.jupiter.api.BeforeAll;

import java.sql.Connection;
import java.sql.DriverManager;

class JdbcTest {
  private static Server server;

  @BeforeAll
  static void beforeAll() throws Exception {
    server = Server.createTcpServer("-tcpAllowOthers").start();
  }

  Connection getConnection() throws Exception{
    return DriverManager.getConnection("jdbc:h2:mem:test", "user", "sa");
  }

}
