package com.bank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ACIDRaceConditionTest extends HibernateTest {
  private static final String BOB_IBAN = "Bob-456";
  private static final String ALICE_IBAN = "Alice-123";

  @BeforeEach
  void createData() {
    createAccounts();
  }

  @AfterEach
  void destroyData() {
    destroyAccounts();
  }

  @Test
  void testSerialExecution() {
    assertEquals(10, getBalanceJPA(ALICE_IBAN));
    assertEquals(0, getBalanceJPA(BOB_IBAN));

    transferUsingJPA(ALICE_IBAN, BOB_IBAN, 5);

    assertEquals(5, getBalanceJPA(ALICE_IBAN));
    assertEquals(5, getBalanceJPA(BOB_IBAN));

    transferUsingJPA(ALICE_IBAN, BOB_IBAN, 5);

    assertEquals(0, getBalanceJPA(ALICE_IBAN));
    assertEquals(10, getBalanceJPA(BOB_IBAN));

    transferUsingJPA(ALICE_IBAN, BOB_IBAN, 5);

    assertEquals(0, getBalanceJPA(ALICE_IBAN));
    assertEquals(10, getBalanceJPA(BOB_IBAN));
  }

  void transferUsingJPA(String fromIban, String toIban, int transferreDolars) {
    Integer fromBalance = getBalanceJPA(fromIban);

    if (fromBalance >= transferreDolars) {
      addJPA(fromIban, -transferreDolars);
      addJPA(toIban, transferreDolars);
    }
  }

  void transferUsingJDBC(String fromIban, String toIban, int transferreDolars) {
    Integer fromBalance = getBalanceJDBC(fromIban);

    if (fromBalance >= transferreDolars) {
      addJDBC(fromIban, -transferreDolars);
      addJDBC(toIban, transferreDolars);
    }
  }

  // -----------------------------------------------------------
  // PARALLEL EXECUTION WITHOUT TRANSACTION
  // 1. This uses JPA
  // Using properties instead of datasource: OK
  // Using datasource: NOK
  @Test
  void testParallelExecutionUsingJPA() {
    assertEquals(10, getBalanceJPA(ALICE_IBAN));
    assertEquals(0, getBalanceJPA(BOB_IBAN));

    parallelExecution(true);

    assertEquals(0, getBalanceJPA(ALICE_IBAN));
    assertEquals(10, getBalanceJPA(BOB_IBAN));
  }

  // 1. This uses JDBC
  // Using properties instead of datasource: OK
  // Using datasource: NOK 
  @Test
  void testParallelExecutionUsingJDBC() {
    assertEquals(10, getBalanceJPA(ALICE_IBAN));
    assertEquals(0, getBalanceJPA(BOB_IBAN));

    parallelExecution(false);

    assertEquals(0, getBalanceJPA(ALICE_IBAN));
    assertEquals(10, getBalanceJPA(BOB_IBAN));
  }

  public void parallelExecution(boolean useJPA) {
    int threadCount = 8;

    String fromIban = ALICE_IBAN;
    String toIban = BOB_IBAN;
    int transferredDollars = 5;

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(() -> {
        awaitOnLatch(startLatch);

        if (useJPA) {
          transferUsingJPA(fromIban, toIban, transferredDollars);
        } else {
          transferUsingJDBC(fromIban, toIban, transferredDollars);
        }

        endLatch.countDown();
      }).start();
    }

    System.out.println("Starting threads...");
    startLatch.countDown();
    awaitOnLatch(endLatch);
  }

  // -----------------------------------------------------------
  // PARALLEL EXECUTION WITH TRANSACTION
  // This uses JDBC
  // Using properties instead of datasource: NOK
  // Using datasource: NOK 
  @Test
  void testParallelExecutionWithTransaction() {
    assertEquals(10, getBalanceJDBC(ALICE_IBAN));
    assertEquals(0, getBalanceJDBC(BOB_IBAN));

    parallelExecutionWithTransaction();

    assertEquals(0, getBalanceJDBC(ALICE_IBAN));
    assertEquals(10, getBalanceJDBC(BOB_IBAN));
  }

  public void parallelExecutionWithTransaction() {
    int threadCount = 8;

    String fromIban = ALICE_IBAN;
    String toIban = BOB_IBAN;
    int transferredDollars = 5;
    

    CountDownLatch workerThreadWaitsAfterReadingBalanceLatch = new CountDownLatch(threadCount);
    CountDownLatch workerThreadWriteBalanceLatch = new CountDownLatch(1);
    CountDownLatch allWorkerThreadsHaveFinishedLatch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(() -> {
        try {
          doInJDBC(connection -> {
            printConnectionDetails(connection);

            workerThreadWaitsAfterReadingBalanceLatch.countDown();
            awaitOnLatch(workerThreadWriteBalanceLatch);
            System.out.println("Running thread");

            int fromBalance = getBalanceJDBC(connection, fromIban);
            if (fromBalance >= transferredDollars) {
              add(connection, fromIban, -transferredDollars);
              add(connection, toIban, transferredDollars);
            }
          });
        } catch (Exception exception) {
          System.err.println("Error transferring money: " + exception.getMessage());
          exception.printStackTrace(System.err);
        }

        allWorkerThreadsHaveFinishedLatch.countDown();
      }).start();
    }

    System.out.println("Starting threads");
    awaitOnLatch(workerThreadWaitsAfterReadingBalanceLatch);
    workerThreadWriteBalanceLatch.countDown();
    awaitOnLatch(allWorkerThreadsHaveFinishedLatch);
  }

  private static void awaitOnLatch(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void createAccounts() {
    doInJpa(entityManager -> {
      Account bobAccount = new Account(BOB_IBAN, "Bob", 0);
      entityManager.persist(bobAccount);

      Account aliceAccount = new Account(ALICE_IBAN, "Alice", 10);
      entityManager.persist(aliceAccount);
    });
  }

  private void destroyAccounts() {
    doInJpa(entityManager -> {
      entityManager.createQuery("DELETE FROM Account")
          .executeUpdate();
    });
  }

  private Integer getBalanceJDBC(String iban) {
    return doInJDBC(connection -> {
      System.out.println(connection);
      printConnectionDetails(connection);
      return getBalanceJDBC(connection, iban);
    });
  }

  private Integer getBalanceJPA(String iban) {
    return doInJpa(entityManager -> {
      return entityManager.createQuery("SELECT a.balance FROM Account a WHERE a.iban = :iban", Integer.class)
          .setParameter("iban", iban)
          .getSingleResult();
    });
  }

  private Integer getBalanceJDBC(Connection connection, String iban) {
    try (PreparedStatement statement = connection.prepareStatement("SELECT balance FROM account WHERE iban = ?")) {

      statement.setString(1, iban);
      ResultSet resultSet = statement.executeQuery();

      if (resultSet.next()) {
        return resultSet.getInt(1);
      }
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
    throw new IllegalArgumentException(String.format("Could not get balance for account IBAN [%s].", iban));
  }

  private void addJDBC(String iban, int dollars) {
    doInJDBC(connection -> {
      System.out.println(connection);
      printConnectionDetails(connection);
      add(connection, iban, dollars);
    });
  }

  private void addJPA(String iban, int dollars) {
    doInJpa(entityManager -> {
      entityManager.createQuery("UPDATE Account a SET a.balance = a.balance + :dollars WHERE a.iban = :iban")
          .setParameter("dollars", dollars)
          .setParameter("iban", iban)
          .executeUpdate();
    });
  }

  private void add(Connection connection, String iban, int balance) {
    try (PreparedStatement statement = connection.prepareStatement("UPDATE account SET balance = balance + ? WHERE iban = ?")) {
      statement.setInt(1, balance);
      statement.setString(2, iban);

      statement.executeUpdate();
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
  }
  private void printConnectionDetails(Connection connection) {
    int isolationLevelIntegerValue;
    boolean autoCommit;
    try {
      isolationLevelIntegerValue = connection.getTransactionIsolation();
      autoCommit = connection.getAutoCommit();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    String isolationLevelStringValue = null;

    switch (isolationLevelIntegerValue) {
      case Connection.TRANSACTION_READ_UNCOMMITTED:
        isolationLevelStringValue = "READ_UNCOMMITTED";
        break;
      case Connection.TRANSACTION_READ_COMMITTED:
        isolationLevelStringValue = "READ_COMMITTED";
        break;
      case Connection.TRANSACTION_REPEATABLE_READ:
        isolationLevelStringValue = "REPEATABLE_READ";
        break;
      case Connection.TRANSACTION_SERIALIZABLE:
        isolationLevelStringValue = "SERIALIZABLE";
        break;
    }

    System.out.println("Connection: " + connection + "; transaction isolation level: " + isolationLevelStringValue + "; autocommit: " + autoCommit);
  }
}
