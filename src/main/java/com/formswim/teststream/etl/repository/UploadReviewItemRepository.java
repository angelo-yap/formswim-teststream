package com.formswim.teststream.etl.repository;

import com.formswim.teststream.etl.model.UploadReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadReviewItemRepository extends JpaRepository<UploadReviewItem, Long> {
}
