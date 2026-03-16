import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class RestoreSchema {
  public static void main(String[] args) throws Exception {
    String url = "jdbc:postgresql://localhost:5432/love_app";
    String user = "postgres";
    String password = "";
    List<String> files = List.of("sql/create_table.sql", "sql/quiz_module_tables.sql");
    try (Connection conn = DriverManager.getConnection(url, user, password)) {
      conn.setAutoCommit(true);
      for (String file : files) {
        String content = Files.readString(Path.of(file));
        StringBuilder cleaned = new StringBuilder();
        for (String line : content.split("\\R")) {
          String trimmed = line.trim();
          if (trimmed.startsWith("--")) {
            continue;
          }
          cleaned.append(line).append('\n');
        }
        for (String stmt : cleaned.toString().split(";")) {
          String sql = stmt.trim();
          if (sql.isEmpty()) continue;
          try (Statement st = conn.createStatement()) {
            st.execute(sql);
          }
        }
      }
      System.out.println("SCHEMA_RESTORED");
    }
  }
}
