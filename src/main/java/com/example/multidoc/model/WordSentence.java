package com.example.multidoc.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

@Entity
@Table(name = "word_sentences")
public class WordSentence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private AnalysisTask task;

    @Column(name = "sentence_index")
    private int sentenceIndex;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_file")
    private String sourceFile;

    @Column(name = "start_position")
    private int startPosition;

    @Column(name = "end_position")
    private int endPosition;

    public WordSentence() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AnalysisTask getTask() {
        return task;
    }

    public void setTask(AnalysisTask task) {
        this.task = task;
    }

    public int getSentenceIndex() {
        return sentenceIndex;
    }

    public void setSentenceIndex(int sentenceIndex) {
        this.sentenceIndex = sentenceIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(int endPosition) {
        this.endPosition = endPosition;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        WordSentence that = (WordSentence) o;
        
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }
        
        return Objects.equals(content, that.content) && 
               Objects.equals(sourceFile, that.sourceFile);
    }
    
    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hash(id);
        }
        return Objects.hash(content, sourceFile);
    }

    @Override
    public String toString() {
        return "WordSentence{" +
                "id=" + id +
                ", task=" + (task != null ? task.getId() : "null") +
                ", sentenceIndex=" + sentenceIndex +
                ", content='" + (content != null ? content.substring(0, Math.min(content.length(), 50)) + "..." : "null") + '\'' +
                ", sourceFile='" + sourceFile + '\'' +
                '}';
    }
} 