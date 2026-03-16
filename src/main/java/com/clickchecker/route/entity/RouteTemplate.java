package com.clickchecker.route.entity;

import com.clickchecker.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
        name = "route_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_route_templates_org_template", columnNames = {"organization_id", "template"})
        }
)
public class RouteTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 512)
    private String template;

    @Column(name = "route_key", nullable = false, length = 512)
    private String routeKey;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean active;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private RouteTemplate(
            Organization organization,
            String template,
            String routeKey,
            Integer priority,
            Boolean active
    ) {
        this.organization = organization;
        this.template = template;
        this.routeKey = routeKey;
        this.priority = priority == null ? 0 : priority;
        this.active = active == null || active;
    }
}
