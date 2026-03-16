import java.sql.*;

public class VerifyAgentExecutionLog {
  public static void main(String[] args) throws Exception {
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/love_app", "postgres", "")) {
      try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select count(*) from agent_execution_log")) {
        rs.next();
        System.out.println("COUNT=" + rs.getInt(1));
      }
    }
  }
}
