import java.sql.*;
public class CheckAgentLogTable {
  public static void main(String[] args) throws Exception {
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/love_app", "postgres", "")) {
      DatabaseMetaData meta = conn.getMetaData();
      try (ResultSet rs = meta.getTables(null, null, "agent_execution_log", new String[]{"TABLE"})) {
        System.out.println(rs.next() ? "EXISTS" : "MISSING");
      }
    }
  }
}
