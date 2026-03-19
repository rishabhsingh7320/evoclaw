package com.omenclawdemo2.repository;

import com.omenclawdemo2.model.Run;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, Long> {
}
