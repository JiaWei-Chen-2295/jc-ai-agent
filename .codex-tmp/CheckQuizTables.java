import java.sql.*;
import java.util.*;
public class CheckQuizTables {
  public static void main(String[] args) throws Exception {
    List<String> tables = List.of(
      "quiz_session",
      "quiz_question",
      "question_response",
      "user_knowledge_state",
      "unmastered_knowledge",
      "agent_execution_log"
    );
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/love_app", "postgres", "")) {
      DatabaseMetaData meta = conn.getMetaData();
      for (String table : tables) {
        try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
          System.out.println(table + "\t" + (rs.next() ? "EXISTS" : "MISSING"));
        }
      }
    }
  }
}
