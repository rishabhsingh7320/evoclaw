package com.openclaw4.repository;

import com.openclaw4.model.Message;
import com.openclaw4.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySessionOrderByCreatedAtAsc(Session session);
}
