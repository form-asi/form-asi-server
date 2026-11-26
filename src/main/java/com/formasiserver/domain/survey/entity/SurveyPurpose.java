package com.formasiserver.domain.survey.entity;

import com.formasiserver.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "survey_purpose", uniqueConstraints = @UniqueConstraint(
        name = "uk_survey_purpose_survey_id", columnNames = "survey_id"))
public class SurveyPurpose extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SurveyPurposeCode purpose;

    @Column(name = "purpose_other", length = 100)
    private String purposeOther;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SurveyMethod method;

    @Column(name = "method_other", length = 100)
    private String methodOther;

    public static SurveyPurpose of(Survey survey, SurveyPurposeCode purpose, String purposeOther,
            SurveyMethod method, String methodOther) {
        SurveyPurpose classification = new SurveyPurpose();
        classification.survey = survey;
        classification.purpose = purpose;
        classification.purposeOther = purposeOther;
        classification.method = method;
        classification.methodOther = methodOther;
        return classification;
    }
}
