package com.goota.zdocspider.repository;

import com.goota.zdocspider.model.entity.Doc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocRepository extends JpaRepository<Doc, UUID>, JpaSpecificationExecutor<Doc> {
    Optional<Doc> findByChecksum(String checksum);
    Optional<Doc> findByFilePath(String filePath);
}
