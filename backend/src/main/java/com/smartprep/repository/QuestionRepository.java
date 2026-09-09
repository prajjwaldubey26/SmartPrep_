package com.smartprep.repository;

import com.smartprep.entity.Question;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findBySessionIdOrderByOrderNoAsc(Long sessionId);
    Optional<Question> findByIdAndSessionId(Long id, Long sessionId);
    long countBySessionId(Long sessionId);
}
