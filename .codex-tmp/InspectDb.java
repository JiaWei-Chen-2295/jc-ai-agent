import java.sql.*;
import java.util.*;

public class InspectDb {
  public static void main(String[] args) throws Exception {
    String url = "jdbc:postgresql://localhost:5432/love_app";
    String user = "postgres";
    String password = "";
    List<String> tables = List.of(
      "agent_execution_log",
      "question_response",
      "quiz_question",
      "quiz_session",
      "tenant",
      "tenant_user",
      "unmastered_knowledge",
      "user",
      "user_knowledge_state"
    );
    try (Connection conn = DriverManager.getConnection(url, user, password)) {
      DatabaseMetaData meta = conn.getMetaData();
      for (String table : tables) {
        boolean exists;
        try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
          exists = rs.next();
        }
        if (!exists) {
          System.out.println(table + "\tMISSING");
          continue;
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select count(*) from \"" + table + "\"")) {
          rs.next();
          System.out.println(table + "\tEXISTS\t" + rs.getInt(1));
        }
      }
    }
  }
}
