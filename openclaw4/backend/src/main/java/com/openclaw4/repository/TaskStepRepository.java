package com.openclaw4.repository;

import com.openclaw4.model.Task;
import com.openclaw4.model.TaskStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskStepRepository extends JpaRepository<TaskStep, Long> {

    List<TaskStep> findByTaskOrderByStepIndexAsc(Task task);
}
