package com.example.multidoc.repository;

import com.example.multidoc.model.FileStorageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileStorageConfigRepository extends JpaRepository<FileStorageConfig, Long> {
    Optional<FileStorageConfig> findByConfigKey(String configKey);
} 