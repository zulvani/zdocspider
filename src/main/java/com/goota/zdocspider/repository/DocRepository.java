package com.goota.zdocspider.repository;

import com.goota.zdocspider.model.entity.Doc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocRepository extends JpaRepository<Doc, UUID>, JpaSpecificationExecutor<Doc> {
    Optional<Doc> findByChecksum(String checksum);
    List<Doc> findAllByChecksumOrderByCreatedAtAsc(String checksum);
    Optional<Doc> findByFilePath(String filePath);

    @Query(value = "SELECT * FROM docs WHERE duplicate_paths IS NOT NULL AND duplicate_paths != '[]' AND duplicate_paths != ''", nativeQuery = true)
    List<Doc> findAllWithDuplicatePaths();
}
