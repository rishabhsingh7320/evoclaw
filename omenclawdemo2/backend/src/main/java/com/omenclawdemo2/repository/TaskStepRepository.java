package com.omenclawdemo2.repository;

import com.omenclawdemo2.model.Task;
import com.omenclawdemo2.model.TaskStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskStepRepository extends JpaRepository<TaskStep, Long> {

    List<TaskStep> findByTaskOrderByStepIndexAsc(Task task);
}
