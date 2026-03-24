package com.clickchecker.eventuser.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class EventUserCommandRepository {

    private static final String FIND_OR_CREATE_USER_ID_SQL = """
            WITH inserted AS (
                INSERT INTO users (organization_id, external_user_id, created_at, updated_at)
                VALUES (:organizationId, :externalUserId, NOW(), NOW())
                ON CONFLICT (organization_id, external_user_id) DO NOTHING
                RETURNING id
            )
            SELECT id
            FROM inserted
            UNION ALL
            SELECT u.id
            FROM users u
            WHERE u.organization_id = :organizationId
              AND u.external_user_id = :externalUserId
            LIMIT 1
            """;

    private final EntityManager entityManager;

    public Long findOrCreateUserId(Long organizationId, String externalUserId) {
        Number result = (Number) entityManager.createNativeQuery(FIND_OR_CREATE_USER_ID_SQL)
                .setParameter("organizationId", organizationId)
                .setParameter("externalUserId", externalUserId)
                .getSingleResult();

        if (result == null) {
            throw new IllegalStateException("event user id resolution failed");
        }

        return result.longValue();
    }
}
