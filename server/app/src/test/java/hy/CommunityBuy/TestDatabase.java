package hy.CommunityBuy;

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
    String env = migration ? "TEST_MIGRATION_DB_URL" : "TEST_DB_URL";
    String url = System.getenv(env);
    if (url != null && !url.isBlank()) {
      String database = migration ? "communibuy_migration_test" : "communibuy_test";
      if (!"yes".equals(System.getenv("ALLOW_TEST_DB_RESET"))
          || !url.matches("jdbc:mysql://[^/]+/" + database + "(\\?.*)?"))
        throw new IllegalStateException(
            "Use an isolated " + database + " and ALLOW_TEST_DB_RESET=yes");
      return new Connection(url, System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"));
    }
    if (System.getenv("TEST_DB_URL") != null && !System.getenv("TEST_DB_URL").isBlank())
      throw new IllegalStateException("TEST_MIGRATION_DB_URL is required with external MySQL");
    var mysql =
        new MySQLContainer<>("mysql:8.4.6")
            .withDatabaseName(migration ? "communibuy_migration_test" : "communibuy_test");
    mysql.start();
    return new Connection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
  }
}
