package com.example.markovstockanalyzer.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "transition_probabilities")
public class TransitionProbabilityEntity {
    @EmbeddedId
    private TransitionProbabilityId id = new TransitionProbabilityId();

    @Column(name = "transition_count", nullable = false, columnDefinition = "int unsigned")
    private Integer transitionCount;

    @Column(name = "probability", nullable = true, columnDefinition = "double")
    private Double probability;

    @MapsId("analysisId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transition_analysis"))
    private AnalysisResultEntity analysis;

    public AnalysisResultEntity getAnalysis() { return analysis; }
    public void setAnalysis(AnalysisResultEntity analysis) { this.analysis = analysis; }

    public TransitionProbabilityEntity() {}

    public TransitionProbabilityId getId() { return id; }
    public void setId(TransitionProbabilityId id) { this.id = id; }
    public Integer getTransitionCount() { return transitionCount; }
    public void setTransitionCount(Integer transitionCount) { this.transitionCount = transitionCount; }
    public Double getProbability() { return probability; }
    public void setProbability(Double probability) { this.probability = probability; }
}
