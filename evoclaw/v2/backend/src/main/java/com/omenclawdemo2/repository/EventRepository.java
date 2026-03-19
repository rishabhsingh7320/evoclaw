package com.omenclawdemo2.repository;

import com.omenclawdemo2.model.Event;
import com.omenclawdemo2.model.Run;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByRunOrderByCreatedAtAsc(Run run);
}
