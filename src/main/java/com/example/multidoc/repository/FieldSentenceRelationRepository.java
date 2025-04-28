package com.example.multidoc.repository;

import com.example.multidoc.model.FieldSentenceRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface FieldSentenceRelationRepository extends JpaRepository<FieldSentenceRelation, Long> {
    
    List<FieldSentenceRelation> findByFieldId(Long fieldId);
    
    List<FieldSentenceRelation> findBySentenceId(Long sentenceId);
    
    @Query("SELECT r FROM FieldSentenceRelation r WHERE r.fieldId = :fieldId ORDER BY r.relevanceScore DESC")
    List<FieldSentenceRelation> findByFieldIdOrderByRelevanceScoreDesc(@Param("fieldId") Long fieldId);
    
    @Query("SELECT r FROM FieldSentenceRelation r WHERE r.sentenceId = :sentenceId ORDER BY r.relevanceScore DESC")
    List<FieldSentenceRelation> findBySentenceIdOrderByRelevanceScoreDesc(@Param("sentenceId") Long sentenceId);
    
    @Transactional
    void deleteByFieldId(Long fieldId);
    
    @Transactional
    void deleteBySentenceId(Long sentenceId);
} 