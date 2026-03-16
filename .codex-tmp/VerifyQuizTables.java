import java.sql.*;
import java.util.*;
public class VerifyQuizTables {
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
      for (String table : tables) {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select count(*) from " + table)) {
          rs.next();
          System.out.println(table + "\tCOUNT=" + rs.getInt(1));
        }
      }
    }
  }
}
