package com.openclawdemo.repository;

import com.openclawdemo.model.Message;
import com.openclawdemo.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findBySessionOrderByCreatedAtAsc(Session session);
}

