package com.omenclawdemo2.repository;

import com.omenclawdemo2.model.Run;
import com.omenclawdemo2.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByRunOrderBySortOrderAsc(Run run);
}
