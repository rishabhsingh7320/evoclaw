package com.openclaw4.repository;

import com.openclaw4.model.Event;
import com.openclaw4.model.Run;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByRunOrderByCreatedAtAsc(Run run);
}
