package com.openclaw3.repository;

import com.openclaw3.model.Memory;
import com.openclaw3.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemoryRepository extends JpaRepository<Memory, Long> {

    Optional<Memory> findBySessionAndKey(Session session, String key);

    List<Memory> findBySessionOrderByUpdatedAtDesc(Session session);
}
