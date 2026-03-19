package com.openclaw4.repository;

import com.openclaw4.model.Run;
import com.openclaw4.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunRepository extends JpaRepository<Run, Long> {

    List<Run> findBySessionAndPhaseIn(Session session, List<String> phases);
}
