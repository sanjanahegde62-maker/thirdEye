package com.thirdeye.backend.repository;

import com.thirdeye.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProject_Id(Long projectId);
}