package hy.CommunityBuy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;
class MigrationTest {
    static final TestDatabase.Connection db=TestDatabase.connect(true);
    @Test void legacySqlSchemaBaselineRetainsAccountsAndConvertsInventoryAndSnapshots() throws Exception { verifyLegacy(false); }
    @Test void legacyHibernateWidthsAndUnsignedTypesMigrate() throws Exception { verifyLegacy(true); }
    private void verifyLegacy(boolean widened) throws Exception {
        var flyway=Flyway.configure().dataSource(db.url(),db.user(),db.password()).cleanDisabled(false).load();
        flyway.clean();
        // Exact e2991cd schema.sql, including UNSIGNED; also exercise JPA's former default varchar(255) widths.
        try(var c=DriverManager.getConnection(db.url(),db.user(),db.password()); var s=c.createStatement()) {
            String schema=new String(getClass().getResourceAsStream("/legacy-e2991cd.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            if (widened) schema=schema.replace("VARCHAR(32)","VARCHAR(255)").replace("VARCHAR(128)","VARCHAR(255)");
            for(String statement:schema.split(";")) if(!statement.isBlank()) s.execute(statement);
            s.executeUpdate("insert into users(id,password,role) values ('Legacy','plain-secret',0)");
            s.executeUpdate("insert into items(name,price,stock,status,owner_id,start_time,end_time) values ('old',100,7,4,'Legacy',0,-1)");
            s.executeUpdate("insert into orders(item_id,user_id,owner_id,quantity,price,address,status,create_time) values (1,'Legacy','Legacy',2,100,'old address',0,100)");
        }
        flyway.baseline(); flyway.migrate(); flyway.validate();
        try(var c=DriverManager.getConnection(db.url(),db.user(),db.password()); var s=c.createStatement()) {
            try(var rs=s.executeQuery("select password,password_scheme,auth_version from users where id='Legacy'")) {
                assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isEqualTo("plain-secret"); assertThat(rs.getString(2)).isEqualTo("LEGACY_PLAIN"); assertThat(rs.getLong(3)).isZero();
            }
            try(var rs=s.executeQuery("select stock,status,shipment_count,end_time from items")) {
                rs.next(); assertThat(rs.getInt(1)).isEqualTo(7); assertThat(rs.getInt(2)).isZero(); assertThat(rs.getLong(3)).isEqualTo(4); assertThat(rs.getObject(4)).isNull();
            }
            try(var rs=s.executeQuery("select total,expires_at,idempotency_key from orders")) {
                rs.next(); assertThat(rs.getLong(1)).isEqualTo(200); assertThat(rs.getLong(2)).isEqualTo(1000); assertThat(rs.getString(3)).isEqualTo("legacy-1");
            }
            assertThatThrownBy(()->s.executeUpdate("insert into items(name,price,stock,status,owner_id,start_time) values ('bad',1,-1,0,'Legacy',0)"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("insert into orders(item_id,user_id,owner_id,quantity,price,total,address,status,create_time,expires_at,idempotency_key,request_hash) values (1,'missing','Legacy',1,1,1,'x',0,1,2,'x','x')"))
                .isInstanceOf(SQLException.class);
        }
    }
}
