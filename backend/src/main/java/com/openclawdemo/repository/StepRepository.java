package com.openclawdemo.repository;

import com.openclawdemo.model.Step;
import com.openclawdemo.model.Run;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StepRepository extends JpaRepository<Step, Long> {

    List<Step> findByRunOrderByStepIndexAsc(Run run);
}

