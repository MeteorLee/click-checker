package com.clickchecker.organizationmember.entity;

import com.clickchecker.account.entity.Account;
import com.clickchecker.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "organization_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_organization_members_account_organization",
                        columnNames = {"account_id", "organization_id"}
                )
        }
)
public class OrganizationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrganizationRole role;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private OrganizationMember(
            Account account,
            Organization organization,
            OrganizationRole role
    ) {
        this.account = account;
        this.organization = organization;
        this.role = role == null ? OrganizationRole.VIEWER : role;
    }

    public boolean isOwner() {
        return this.role == OrganizationRole.OWNER;
    }

    public boolean hasRoleAtLeast(OrganizationRole requiredRole) {
        return this.role.isAtLeast(requiredRole);
    }

    public void changeRole(OrganizationRole role) {
        this.role = role;
    }
}
