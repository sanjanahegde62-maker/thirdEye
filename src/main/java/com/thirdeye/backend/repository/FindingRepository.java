package com.thirdeye.backend.repository;

import com.thirdeye.backend.entity.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FindingRepository extends JpaRepository<Finding, Long> {

    List<Finding> findByReview_Id(Long reviewId);

    /** Removes all findings for a review — used before re-running analysis to prevent duplicates. */
    @Transactional
    void deleteByReview_Id(Long reviewId);

    /** Count findings per severity for a given review (used by summary endpoint). */
    long countByReview_IdAndSeverityIgnoreCase(Long reviewId, String severity);
}