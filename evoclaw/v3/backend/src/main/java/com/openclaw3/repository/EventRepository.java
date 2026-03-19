package com.openclaw3.repository;

import com.openclaw3.model.Event;
import com.openclaw3.model.Run;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByRunOrderByCreatedAtAsc(Run run);
}
