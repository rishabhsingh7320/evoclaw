package com.openclawdemo.repository;

import com.openclawdemo.model.Event;
import com.openclawdemo.model.Run;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByRunOrderByCreatedAtAsc(Run run);
}

