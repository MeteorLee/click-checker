package com.clickchecker.organizationmember.repository;

import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.QOrganizationMember;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class OrganizationMemberQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<OrganizationMember> findMembershipsByAccountId(Long accountId) {
        QOrganizationMember organizationMember = QOrganizationMember.organizationMember;

        return queryFactory
                .selectFrom(organizationMember)
                .join(organizationMember.organization).fetchJoin()
                .where(organizationMember.account.id.eq(accountId))
                .orderBy(organizationMember.organization.name.asc(), organizationMember.id.asc())
                .fetch();
    }

    public List<OrganizationMember> findMembersByOrganizationId(Long organizationId) {
        QOrganizationMember organizationMember = QOrganizationMember.organizationMember;

        return queryFactory
                .selectFrom(organizationMember)
                .join(organizationMember.account).fetchJoin()
                .where(organizationMember.organization.id.eq(organizationId))
                .orderBy(organizationMember.account.loginId.asc(), organizationMember.id.asc())
                .fetch();
    }
}
