package com.tn.service.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.tn.service.autoconfig.ControllerAutoConfiguration;

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  classes = {ControllerAutoConfiguration.class, DatasourceReadinessIntegrationTest.DatabaseConfiguration.class}
)
@AutoConfigureTestRestTemplate
@EnableAutoConfiguration
class DatasourceReadinessIntegrationTest
{
  static final AtomicBoolean DATABASE_UP = new AtomicBoolean(true);

  @Autowired
  TestRestTemplate testRestTemplate;

  @AfterEach
  void restoreDatabase()
  {
    DATABASE_UP.set(true);
  }

  @Test
  void shouldBeReady_whenTheDatasourceIsReachable()
  {
    assertEquals(HttpStatus.OK.value(), status("/actuator/health/readiness"));
    assertEquals(HttpStatus.OK.value(), status("/actuator/health/liveness"));
  }

  @Test
  void shouldNotBeReadyButStillLive_whenTheDatasourceCannotBeReached()
  {
    DATABASE_UP.set(false);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), status("/actuator/health/readiness"));
    assertEquals(HttpStatus.OK.value(), status("/actuator/health/liveness"));
    assertEquals("{\"status\":\"DOWN\"}", testRestTemplate.getForEntity("/actuator/health/readiness", String.class).getBody());
  }

  @Test
  void shouldBeReadyAgain_whenTheDatasourceIsBack()
  {
    DATABASE_UP.set(false);
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), status("/actuator/health/readiness"));

    DATABASE_UP.set(true);
    assertEquals(HttpStatus.OK.value(), status("/actuator/health/readiness"));
  }

  private int status(String path)
  {
    return testRestTemplate.getForEntity(path, String.class).getStatusCode().value();
  }

  @TestConfiguration
  static class DatabaseConfiguration
  {
    @Bean
    DataSource dataSource()
    {
      return new SwitchedDataSource(new DriverManagerDataSource("jdbc:h2:mem:health;DB_CLOSE_DELAY=-1", "sa", ""), DATABASE_UP);
    }
  }

  /** A datasource that can be made unreachable and reachable again. */
  record SwitchedDataSource(DataSource delegate, AtomicBoolean up) implements DataSource
  {
    @Override
    public Connection getConnection() throws SQLException
    {
      return connected(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException
    {
      return connected(delegate.getConnection(username, password));
    }

    private Connection connected(Connection connection) throws SQLException
    {
      if (!up.get())
      {
        connection.close();
        throw new SQLException("The database is down");
      }
      return connection;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException
    {
      return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException
    {
      delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException
    {
      delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException
    {
      return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException
    {
      return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException
    {
      return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
      return delegate.isWrapperFor(iface);
    }
  }
}
