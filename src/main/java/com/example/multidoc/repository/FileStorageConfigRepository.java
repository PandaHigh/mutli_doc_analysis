package com.example.multidoc.repository;

import com.example.multidoc.model.FileStorageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileStorageConfigRepository extends JpaRepository<FileStorageConfig, Long> {
} 