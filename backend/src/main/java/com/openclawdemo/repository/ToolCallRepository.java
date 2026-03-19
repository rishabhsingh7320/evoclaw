package com.openclawdemo.repository;

import com.openclawdemo.model.ToolCall;
import com.openclawdemo.model.Step;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolCallRepository extends JpaRepository<ToolCall, Long> {

    List<ToolCall> findByStep(Step step);
}

