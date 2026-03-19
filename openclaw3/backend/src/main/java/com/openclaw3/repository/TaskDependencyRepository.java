package com.openclaw3.repository;

import com.openclaw3.model.Task;
import com.openclaw3.model.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {

    List<TaskDependency> findByTask(Task task);

    List<TaskDependency> findByTaskIn(List<Task> tasks);
}
