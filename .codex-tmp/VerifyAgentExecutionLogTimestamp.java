import java.sql.*;
public class VerifyAgentExecutionLogTimestamp {
  public static void main(String[] args) throws Exception {
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/love_app", "postgres", "")) {
      DatabaseMetaData meta = conn.getMetaData();
      try (ResultSet rs = meta.getColumns(null, null, "agent_execution_log", "timestamp")) {
        System.out.println(rs.next() ? "TIMESTAMP_EXISTS" : "TIMESTAMP_MISSING");
      }
    }
  }
}
