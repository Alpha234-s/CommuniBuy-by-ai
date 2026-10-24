package hy.CommunityBuy;

import java.util.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

final class TestDatabase {
  private TestDatabase() {}

  static void reset(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    if (!"communibuy_test".equals(jdbc.queryForObject("select database()", String.class)))
      throw new IllegalStateException("Not the disposable business test schema");
    for (String table :
        java.util.List.of(
            "audit_events",
            "channel_events",
            "simulator_operations",
            "settlement_entries",
            "settlement_batches",
            "reviews",
            "refunds",
            "payments",
            "order_lines",
            "cart_entries",
            "orders",
            "checkout_batches",
            "items",
            "categories",
            "blacklist",
            "users")) jdbc.update("DELETE FROM " + table);
  }

  record Connection(String url, String user, String password) {}

  static Connection connect(boolean migration) {
    var external = external(System.getenv(), migration);
    if (external.isPresent()) {
      var db = external.get();
      try (var ignored = java.sql.DriverManager.getConnection(db.url(), db.user(), db.password())) {
        return db;
      } catch (java.sql.SQLException unavailable) {
        throw new IllegalStateException(
            "Cannot connect to disposable MySQL; check TEST_DB_URL, TEST_MIGRATION_DB_URL and"
                + " TEST_DB_USER/TEST_DB_PASSWORD. No tests were skipped.");
      }
    }
    if (!DockerClientFactory.instance().isDockerAvailable())
      throw new IllegalStateException(
          "Docker is unavailable. Start Docker, or configure both TEST_DB_URL (communibuy_test) and"
              + " TEST_MIGRATION_DB_URL (communibuy_migration_test), TEST_DB_USER/TEST_DB_PASSWORD"
              + " and ALLOW_TEST_DB_RESET=yes. No tests were skipped.");
    var mysql =
        new MySQLContainer<>("mysql:8.4.6")
            .withDatabaseName(migration ? "communibuy_migration_test" : "communibuy_test");
    mysql.start();
    return new Connection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
  }

  static Optional<Connection> external(Map<String, String> env, boolean migration) {
    String business = env.getOrDefault("TEST_DB_URL", ""),
        upgrade = env.getOrDefault("TEST_MIGRATION_DB_URL", "");
    if (business.isBlank() && upgrade.isBlank()) return Optional.empty();
    if (business.isBlank() || upgrade.isBlank())
      throw new IllegalStateException(
          "Both TEST_DB_URL and TEST_MIGRATION_DB_URL are required with external MySQL");
    if (!"yes".equals(env.get("ALLOW_TEST_DB_RESET"))
        || !business.matches("jdbc:mysql://[^/]+/communibuy_test(\\?.*)?")
        || !upgrade.matches("jdbc:mysql://[^/]+/communibuy_migration_test(\\?.*)?"))
      throw new IllegalStateException(
          "Use isolated communibuy_test and communibuy_migration_test schemas and"
              + " ALLOW_TEST_DB_RESET=yes");
    if (env.getOrDefault("TEST_DB_USER", "").isBlank()
        || env.getOrDefault("TEST_DB_PASSWORD", "").isBlank())
      throw new IllegalStateException(
          "TEST_DB_USER and TEST_DB_PASSWORD must be configured for disposable MySQL");
    return Optional.of(
        new Connection(
            migration ? upgrade : business, env.get("TEST_DB_USER"), env.get("TEST_DB_PASSWORD")));
  }
}
