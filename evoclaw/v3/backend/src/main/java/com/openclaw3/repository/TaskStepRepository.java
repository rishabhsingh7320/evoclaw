package com.openclaw3.repository;

import com.openclaw3.model.Task;
import com.openclaw3.model.TaskStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskStepRepository extends JpaRepository<TaskStep, Long> {

    List<TaskStep> findByTaskOrderByStepIndexAsc(Task task);
}
