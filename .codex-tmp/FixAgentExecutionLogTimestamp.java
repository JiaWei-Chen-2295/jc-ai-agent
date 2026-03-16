import java.sql.*;
public class FixAgentExecutionLogTimestamp {
  public static void main(String[] args) throws Exception {
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/love_app", "postgres", "")) {
      try (Statement st = conn.createStatement()) {
        st.execute("ALTER TABLE agent_execution_log ADD COLUMN IF NOT EXISTS timestamp TIMESTAMP NULL");
      }
      System.out.println("TIMESTAMP_FIXED");
    }
  }
}
