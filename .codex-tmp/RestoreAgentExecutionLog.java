import java.sql.*;

public class RestoreAgentExecutionLog {
  public static void main(String[] args) throws Exception {
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/love_app", "postgres", "")) {
      try (Statement st = conn.createStatement()) {
        st.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        st.execute("""
          CREATE TABLE IF NOT EXISTS agent_execution_log (
              id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
              session_id UUID NOT NULL,
              tenant_id BIGINT NOT NULL,
              iteration INT NOT NULL,
              phase VARCHAR(32) NOT NULL,
              tool_name VARCHAR(64) NULL,
              input_data JSONB NULL,
              output_data JSONB NULL,
              execution_time_ms INT NULL,
              create_time TIMESTAMP NOT NULL DEFAULT NOW(),
              update_time TIMESTAMP NOT NULL DEFAULT NOW(),
              is_delete SMALLINT NOT NULL DEFAULT 0
          )
        """);
        st.execute("CREATE INDEX IF NOT EXISTS idx_agent_execution_log_session ON agent_execution_log(session_id) WHERE is_delete = 0");
        st.execute("CREATE INDEX IF NOT EXISTS idx_agent_execution_log_phase ON agent_execution_log(phase) WHERE is_delete = 0");
        st.execute("COMMENT ON TABLE agent_execution_log IS 'Agent执行日志(ReAct循环记录) - JPA Entity: AgentExecutionLog'");
        st.execute("COMMENT ON COLUMN agent_execution_log.phase IS 'ReAct阶段: THOUGHT/ACTION/OBSERVATION'");
        st.execute("COMMENT ON COLUMN agent_execution_log.input_data IS '输入数据 (JSONB -> Map<String, Object>)'");
        st.execute("COMMENT ON COLUMN agent_execution_log.output_data IS '输出数据 (JSONB -> Map<String, Object>)'");
      }
      DatabaseMetaData meta = conn.getMetaData();
      try (ResultSet rs = meta.getTables(null, null, "agent_execution_log", new String[]{"TABLE"})) {
        System.out.println(rs.next() ? "RESTORED" : "FAILED");
      }
    }
  }
}
