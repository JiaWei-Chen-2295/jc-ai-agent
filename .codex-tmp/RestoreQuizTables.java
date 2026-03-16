import java.sql.*;
import java.util.*;

public class RestoreQuizTables {
  public static void main(String[] args) throws Exception {
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/love_app", "postgres", "")) {
      try (Statement st = conn.createStatement()) {
        st.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");

        st.execute("""
          CREATE TABLE IF NOT EXISTS quiz_session (
              current_question_no integer NOT NULL,
              is_delete integer NOT NULL,
              score integer NOT NULL,
              total_questions integer NOT NULL,
              completed_at timestamp(6),
              create_time timestamp(6) NOT NULL,
              started_at timestamp(6),
              tenant_id bigint NOT NULL,
              update_time timestamp(6) NOT NULL,
              user_id bigint NOT NULL,
              id uuid NOT NULL,
              quiz_mode varchar(32) NOT NULL,
              status varchar(32) NOT NULL,
              agent_state jsonb,
              document_scope jsonb,
              PRIMARY KEY (id),
              CONSTRAINT quiz_session_quiz_mode_check CHECK (quiz_mode in ('EASY','MEDIUM','HARD','ADAPTIVE')),
              CONSTRAINT quiz_session_status_check CHECK (status in ('IN_PROGRESS','COMPLETED','PAUSED','TIMEOUT','ABANDONED'))
          )
        """);

        st.execute("""
          CREATE TABLE IF NOT EXISTS quiz_question (
              is_delete integer NOT NULL,
              question_no integer NOT NULL,
              create_time timestamp(6) NOT NULL,
              source_doc_id bigint,
              tenant_id bigint NOT NULL,
              update_time timestamp(6) NOT NULL,
              id uuid NOT NULL,
              session_id uuid NOT NULL,
              source_chunk_id uuid,
              difficulty varchar(32) NOT NULL,
              question_type varchar(32) NOT NULL,
              related_concept varchar(256),
              correct_answer TEXT NOT NULL,
              explanation TEXT,
              question_text TEXT NOT NULL,
              options jsonb,
              PRIMARY KEY (id),
              CONSTRAINT quiz_question_difficulty_check CHECK (difficulty in ('EASY','MEDIUM','HARD')),
              CONSTRAINT quiz_question_type_check CHECK (question_type in ('SINGLE_CHOICE','MULTIPLE_SELECT','TRUE_FALSE','FILL_IN_BLANK','SHORT_ANSWER','EXPLANATION','MATCHING','ORDERING','CODE_COMPLETION'))
          )
        """);

        st.execute("""
          CREATE TABLE IF NOT EXISTS question_response (
              confusion_detected boolean NOT NULL,
              hesitation_detected boolean NOT NULL,
              is_correct boolean NOT NULL,
              is_delete integer NOT NULL,
              response_time_ms integer NOT NULL,
              score integer NOT NULL,
              create_time timestamp(6) NOT NULL,
              tenant_id bigint NOT NULL,
              update_time timestamp(6) NOT NULL,
              user_id bigint NOT NULL,
              id uuid NOT NULL,
              question_id uuid NOT NULL,
              session_id uuid NOT NULL,
              agent_action varchar(32),
              concept_mastery varchar(32),
              feedback TEXT,
              user_answer TEXT NOT NULL,
              PRIMARY KEY (id),
              CONSTRAINT question_response_concept_mastery_check CHECK (concept_mastery in ('MASTERED','PARTIAL','UNMASTERED'))
          )
        """);

        st.execute("""
          CREATE TABLE IF NOT EXISTS user_knowledge_state (
              cognitive_load_score integer NOT NULL,
              correct_answers integer NOT NULL,
              is_delete integer NOT NULL,
              stability_score integer NOT NULL,
              total_questions integer NOT NULL,
              understanding_depth integer NOT NULL,
              create_time timestamp(6) NOT NULL,
              tenant_id bigint NOT NULL,
              update_time timestamp(6) NOT NULL,
              user_id bigint NOT NULL,
              id uuid NOT NULL,
              topic_type varchar(32) NOT NULL,
              topic_id varchar(128) NOT NULL,
              topic_name varchar(256),
              PRIMARY KEY (id),
              CONSTRAINT uk_user_knowledge_state UNIQUE (tenant_id, user_id, topic_type, topic_id),
              CONSTRAINT user_knowledge_state_topic_type_check CHECK (topic_type in ('DOCUMENT','CONCEPT'))
          )
        """);

        st.execute("""
          CREATE TABLE IF NOT EXISTS unmastered_knowledge (
              failure_count integer NOT NULL,
              is_delete integer NOT NULL,
              create_time timestamp(6) NOT NULL,
              resolved_at timestamp(6),
              source_doc_id bigint,
              tenant_id bigint NOT NULL,
              update_time timestamp(6) NOT NULL,
              user_id bigint NOT NULL,
              id uuid NOT NULL,
              source_session_id uuid,
              gap_type varchar(32),
              severity varchar(32),
              status varchar(32) NOT NULL,
              concept_name varchar(256) NOT NULL,
              gap_description TEXT,
              root_cause TEXT,
              PRIMARY KEY (id),
              CONSTRAINT unmastered_knowledge_gap_type_check CHECK (gap_type in ('CONCEPTUAL','PROCEDURAL','BOUNDARY')),
              CONSTRAINT unmastered_knowledge_severity_check CHECK (severity in ('HIGH','MEDIUM','LOW')),
              CONSTRAINT unmastered_knowledge_status_check CHECK (status in ('ACTIVE','RESOLVED'))
          )
        """);

        st.execute("CREATE INDEX IF NOT EXISTS idx_quiz_session_tenant_user ON quiz_session (tenant_id, user_id)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_quiz_session_status ON quiz_session (status)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_quiz_session_user_time ON quiz_session (tenant_id, user_id, create_time)");

        st.execute("CREATE INDEX IF NOT EXISTS idx_quiz_question_session ON quiz_question (session_id)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_quiz_question_tenant ON quiz_question (tenant_id)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_quiz_question_type ON quiz_question (question_type)");

        st.execute("CREATE INDEX IF NOT EXISTS idx_question_response_session ON question_response (session_id)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_question_response_question ON question_response (question_id)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_question_response_user ON question_response (user_id)");

        st.execute("CREATE INDEX IF NOT EXISTS idx_user_knowledge_state_user ON user_knowledge_state (tenant_id, user_id)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_user_knowledge_state_topic ON user_knowledge_state (topic_type, topic_id)");

        st.execute("CREATE INDEX IF NOT EXISTS idx_unmastered_knowledge_user ON unmastered_knowledge (tenant_id, user_id)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_unmastered_knowledge_status ON unmastered_knowledge (status)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_unmastered_knowledge_concept ON unmastered_knowledge (concept_name)");
      }

      DatabaseMetaData meta = conn.getMetaData();
      for (String table : List.of("quiz_session", "quiz_question", "question_response", "user_knowledge_state", "unmastered_knowledge")) {
        try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
          System.out.println(table + "\t" + (rs.next() ? "RESTORED" : "FAILED"));
        }
      }
    }
  }
}
