package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.WordChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface WordChunkRepository extends JpaRepository<WordChunk, Long> {
    List<WordChunk> findByTask(AnalysisTask task);
    List<WordChunk> findByTaskAndSourceFileOrderByChunkIndex(AnalysisTask task, String sourceFile);
    long countByTask(AnalysisTask task);

    // Added for resumability
    List<WordChunk> findByTaskAndStatusIn(AnalysisTask task, List<WordChunk.ChunkStatus> statuses);

    // Added for progress/error checking
    long countByTaskAndStatus(AnalysisTask task, WordChunk.ChunkStatus status);

    // Added for task deletion/reprocessing
    @Transactional
    void deleteByTask(AnalysisTask task);
} 