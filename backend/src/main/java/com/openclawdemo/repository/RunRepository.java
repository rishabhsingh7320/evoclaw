package com.openclawdemo.repository;

import com.openclawdemo.model.Run;
import com.openclawdemo.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunRepository extends JpaRepository<Run, Long> {

    List<Run> findBySessionOrderByStartedAtDesc(Session session);
}

