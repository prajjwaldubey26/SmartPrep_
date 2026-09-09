package com.smartprep.repository;

import com.smartprep.entity.Answer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    Optional<Answer> findByQuestionId(Long questionId);
    List<Answer> findByQuestionIdIn(List<Long> questionIds);
}
