package com.openclaw3.repository;

import com.openclaw3.model.Run;
import com.openclaw3.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByRunOrderBySortOrderAsc(Run run);
}
