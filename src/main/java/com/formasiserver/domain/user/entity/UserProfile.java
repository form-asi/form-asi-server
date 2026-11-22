package com.formasiserver.domain.user.entity;

import com.formasiserver.domain.user.entity.profile.ChildrenCount;
import com.formasiserver.domain.user.entity.profile.DriverLicense;
import com.formasiserver.domain.user.entity.profile.Gender;
import com.formasiserver.domain.user.entity.profile.HouseholdComposition;
import com.formasiserver.domain.user.entity.profile.HouseholdSize;
import com.formasiserver.domain.user.entity.profile.MaritalStatus;
import com.formasiserver.domain.user.entity.profile.Occupation;
import com.formasiserver.domain.user.entity.profile.PetExperience;
import com.formasiserver.domain.user.entity.profile.Region;
import com.formasiserver.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_profile")
public class UserProfile extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "job", nullable = false, length = 40)
    private Occupation occupation;

    @Column(name = "job_etc", length = 20)
    private String occupationOther;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(name = "household_type", length = 32)
    private HouseholdComposition householdComposition;

    @Column(name = "household_etc", length = 20)
    private String householdCompositionOther;

    @Enumerated(EnumType.STRING)
    @Column(name = "household_size", length = 20)
    private HouseholdSize householdSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    @Column(name = "marital_status_etc", length = 20)
    private String maritalStatusOther;

    @Enumerated(EnumType.STRING)
    @Column(name = "children", length = 20)
    private ChildrenCount childrenCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "pet_experience", length = 20)
    private PetExperience petExperience;

    @Enumerated(EnumType.STRING)
    @Column(name = "driver_license", length = 20)
    private DriverLicense driverLicense;

    @Builder
    private UserProfile(
            User user,
            Gender gender,
            Occupation occupation,
            @Nullable String occupationOther,
            @Nullable Region region,
            @Nullable HouseholdComposition householdComposition,
            @Nullable String householdCompositionOther,
            @Nullable HouseholdSize householdSize,
            @Nullable MaritalStatus maritalStatus,
            @Nullable String maritalStatusOther,
            @Nullable ChildrenCount childrenCount,
            @Nullable PetExperience petExperience,
            @Nullable DriverLicense driverLicense
    ) {
        this.user = user;
        update(gender, occupation, occupationOther, region, householdComposition, householdCompositionOther,
                householdSize, maritalStatus, maritalStatusOther, childrenCount, petExperience, driverLicense);
    }

    public void update(
            Gender gender,
            Occupation occupation,
            @Nullable String occupationOther,
            @Nullable Region region,
            @Nullable HouseholdComposition householdComposition,
            @Nullable String householdCompositionOther,
            @Nullable HouseholdSize householdSize,
            @Nullable MaritalStatus maritalStatus,
            @Nullable String maritalStatusOther,
            @Nullable ChildrenCount childrenCount,
            @Nullable PetExperience petExperience,
            @Nullable DriverLicense driverLicense
    ) {
        this.gender = gender;
        this.occupation = occupation;
        this.occupationOther = occupationOther;
        this.region = region;
        this.householdComposition = householdComposition;
        this.householdCompositionOther = householdCompositionOther;
        this.householdSize = householdSize;
        this.maritalStatus = maritalStatus;
        this.maritalStatusOther = maritalStatusOther;
        this.childrenCount = childrenCount;
        this.petExperience = petExperience;
        this.driverLicense = driverLicense;
    }
}
