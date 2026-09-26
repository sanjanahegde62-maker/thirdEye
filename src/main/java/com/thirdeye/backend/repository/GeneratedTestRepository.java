package com.thirdeye.backend.repository;

import com.thirdeye.backend.entity.GeneratedTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GeneratedTestRepository extends JpaRepository<GeneratedTest, Long> {

    List<GeneratedTest> findByReview_Id(Long reviewId);

    @Transactional
    void deleteByReview_Id(Long reviewId);
}
