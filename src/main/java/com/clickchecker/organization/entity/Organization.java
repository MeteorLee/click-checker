package com.clickchecker.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 32, unique = true)
    private String apiKeyKid;

    @Column(length = 64, unique = true)
    private String apiKeyHash;

    @Column(length = 16)
    private String apiKeyPrefix;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ApiKeyStatus apiKeyStatus = ApiKeyStatus.ACTIVE;

    private Instant apiKeyCreatedAt;

    private Instant apiKeyRotatedAt;

    private Instant apiKeyLastUsedAt;

    @Builder
    private Organization(String name, String apiKeyKid, String apiKeyHash, String apiKeyPrefix,
                         ApiKeyStatus apiKeyStatus, Instant apiKeyCreatedAt,
                         Instant apiKeyRotatedAt, Instant apiKeyLastUsedAt,
                         Instant createdAt, Instant updatedAt) {
        this.name = name;
        this.apiKeyKid = apiKeyKid;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyPrefix = apiKeyPrefix;
        this.apiKeyStatus = apiKeyStatus == null ? ApiKeyStatus.ACTIVE : apiKeyStatus;
        this.apiKeyCreatedAt = apiKeyCreatedAt;
        this.apiKeyRotatedAt = apiKeyRotatedAt;
        this.apiKeyLastUsedAt = apiKeyLastUsedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void rotateApiKey(String apiKeyKid, String apiKeyHash, String apiKeyPrefix, Instant now) {
        this.apiKeyKid = apiKeyKid;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyPrefix = apiKeyPrefix;
        this.apiKeyStatus = ApiKeyStatus.ACTIVE;
        this.apiKeyCreatedAt = now;
        this.apiKeyRotatedAt = now;
    }

    public void markApiKeyUsed(Instant now) {
        this.apiKeyLastUsedAt = now;
    }

    public void disableApiKey() {
        this.apiKeyStatus = ApiKeyStatus.DISABLED;
    }

    public void touchApiKeyCreatedAtIfAbsent() {
        if (this.apiKeyCreatedAt == null && this.apiKeyHash != null) {
            this.apiKeyCreatedAt = Instant.now();
        }
    }
}
