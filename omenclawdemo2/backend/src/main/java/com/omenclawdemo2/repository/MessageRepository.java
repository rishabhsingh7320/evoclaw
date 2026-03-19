package com.omenclawdemo2.repository;

import com.omenclawdemo2.model.Message;
import com.omenclawdemo2.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySessionOrderByCreatedAtAsc(Session session);
}
