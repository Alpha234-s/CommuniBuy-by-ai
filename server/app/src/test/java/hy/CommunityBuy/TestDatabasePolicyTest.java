package hy.CommunityBuy;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class TestDatabasePolicyTest {
  Map<String, String> valid() {
    return new HashMap<>(
        Map.of(
            "TEST_DB_URL",
            "jdbc:mysql://localhost/communibuy_test",
            "TEST_MIGRATION_DB_URL",
            "jdbc:mysql://localhost/communibuy_migration_test",
            "TEST_DB_USER",
            "scoped-test-user",
            "TEST_DB_PASSWORD",
            "test-only-secret",
            "ALLOW_TEST_DB_RESET",
            "yes"));
  }

  @Test
  void absentConfigurationSelectsDockerWithoutConnecting() {
    assertThat(TestDatabase.external(Map.of(), false)).isEmpty();
    assertThat(TestDatabase.external(Map.of(), true)).isEmpty();
  }

  @Test
  void bothExternalSchemasAndExplicitResetAreRequired() {
    for (String key : List.of("TEST_DB_URL", "TEST_MIGRATION_DB_URL", "ALLOW_TEST_DB_RESET")) {
      var env = valid();
      env.remove(key);
      assertThatThrownBy(() -> TestDatabase.external(env, false))
          .isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> TestDatabase.external(env, true))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void unsafeSchemaAndMissingCredentialsFailWithoutEchoingSecrets() {
    for (String key :
        List.of("TEST_DB_URL", "TEST_MIGRATION_DB_URL", "TEST_DB_USER", "TEST_DB_PASSWORD")) {
      var env = valid();
      env.put(
          key,
          key.endsWith("URL") ? "jdbc:mysql://localhost/production?password=never-log-this" : "");
      assertThatThrownBy(() -> TestDatabase.external(env, false))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageNotContaining("never-log-this")
          .hasMessageNotContaining("test-only-secret");
    }
  }

  @Test
  void authorizedSettingsChooseTheirOwnSchema() {
    assertThat(TestDatabase.external(valid(), false).orElseThrow().url())
        .endsWith("/communibuy_test");
    assertThat(TestDatabase.external(valid(), true).orElseThrow().url())
        .endsWith("/communibuy_migration_test");
  }
}
