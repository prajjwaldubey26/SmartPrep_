-- PostgreSQL schema for SMARTPREP (Supabase / Neon / local Postgres).
-- Hibernate ddl-auto=update can also create these tables automatically.

CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(180) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  target_role VARCHAR(120),
  experience_level VARCHAR(60),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS interview_sessions (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  role VARCHAR(120) NOT NULL,
  difficulty VARCHAR(40) NOT NULL,
  status VARCHAR(40) NOT NULL,
  average_score DOUBLE PRECISION,
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ended_at TIMESTAMPTZ NULL
);

CREATE TABLE IF NOT EXISTS questions (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES interview_sessions(id),
  question_text TEXT NOT NULL,
  category VARCHAR(80),
  order_no INT NOT NULL
);

CREATE TABLE IF NOT EXISTS answers (
  id BIGSERIAL PRIMARY KEY,
  question_id BIGINT NOT NULL REFERENCES questions(id),
  answer_text TEXT NOT NULL,
  answered_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evaluations (
  id BIGSERIAL PRIMARY KEY,
  answer_id BIGINT NOT NULL UNIQUE REFERENCES answers(id),
  score INT NOT NULL,
  feedback TEXT,
  strengths TEXT,
  improvements TEXT,
  verdict VARCHAR(40),
  why_right TEXT,
  why_wrong TEXT,
  better_answer TEXT,
  spoken_feedback TEXT
);

CREATE INDEX IF NOT EXISTS idx_sessions_user_started
  ON interview_sessions (user_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_questions_session_order
  ON questions (session_id, order_no);
