package com.smartprep.repository;

import com.smartprep.entity.Evaluation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    Optional<Evaluation> findByAnswerId(Long answerId);
    List<Evaluation> findByAnswerIdIn(List<Long> answerIds);
}
