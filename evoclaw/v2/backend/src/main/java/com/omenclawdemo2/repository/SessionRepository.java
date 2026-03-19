package com.omenclawdemo2.repository;

import com.omenclawdemo2.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, Long> {
}
