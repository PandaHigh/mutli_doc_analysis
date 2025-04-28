package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.WordSentence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WordSentenceRepository extends JpaRepository<WordSentence, Long> {

    List<WordSentence> findByTask(AnalysisTask task);
    
    void deleteByTask(AnalysisTask task);
    
    @Query("SELECT s FROM WordSentence s WHERE s.task = :task ORDER BY s.sentenceIndex")
    List<WordSentence> findByTaskOrderBySentenceIndex(@Param("task") AnalysisTask task);
    
    @Query("SELECT s FROM WordSentence s WHERE s.task = :task AND s.sourceFile = :sourceFile ORDER BY s.sentenceIndex")
    List<WordSentence> findByTaskAndSourceFile(@Param("task") AnalysisTask task, @Param("sourceFile") String sourceFile);
} 