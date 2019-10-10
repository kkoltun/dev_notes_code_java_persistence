package com.hr.jdbc;

import org.apache.ibatis.jdbc.ScriptRunner;
import org.h2.tools.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.apache.ibatis.io.Resources.getResourceAsReader;

class JdbcTest {
  private static Server server;

  @BeforeEach
  void beforeEach() throws Exception {
    ScriptRunner runner = new ScriptRunner(getConnection());

    runner.setAutoCommit(true);
    runner.setStopOnError(true);
    runner.runScript(getResourceAsReader("init-hr-schema.sql"));

    runner.closeConnection();
  }

  @BeforeAll
  static void beforeAll() throws Exception {
    server = Server.createTcpServer("-tcpAllowOthers").start();
  }

  @AfterAll
  static void afterAll() throws Exception {
    server.stop();
  }

  Connection getConnection() throws Exception{
    return DriverManager.getConnection("jdbc:h2:tcp://localhost/~/hr;SCHEMA=hr", "user", "sa");
  }

}
