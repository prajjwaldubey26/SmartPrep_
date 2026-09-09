CREATE DATABASE IF NOT EXISTS smartprep CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smartprep;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(180) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  target_role VARCHAR(120),
  experience_level VARCHAR(60),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role VARCHAR(120) NOT NULL,
  difficulty VARCHAR(40) NOT NULL,
  status VARCHAR(40) NOT NULL,
  average_score DOUBLE,
  started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  ended_at TIMESTAMP NULL,
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS questions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  question_text TEXT NOT NULL,
  category VARCHAR(80),
  order_no INT NOT NULL,
  CONSTRAINT fk_question_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id)
);

CREATE TABLE IF NOT EXISTS answers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question_id BIGINT NOT NULL,
  answer_text TEXT NOT NULL,
  answered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES questions(id)
);

CREATE TABLE IF NOT EXISTS evaluations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  answer_id BIGINT NOT NULL UNIQUE,
  score INT NOT NULL,
  feedback TEXT,
  strengths TEXT,
  improvements TEXT,
  CONSTRAINT fk_eval_answer FOREIGN KEY (answer_id) REFERENCES answers(id)
);
