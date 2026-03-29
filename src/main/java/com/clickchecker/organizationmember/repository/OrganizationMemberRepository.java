package com.clickchecker.organizationmember.repository;

import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    List<OrganizationMember> findAllByAccountId(Long accountId);

    List<OrganizationMember> findAllByOrganizationId(Long organizationId);

    Optional<OrganizationMember> findByAccountIdAndOrganizationId(Long accountId, Long organizationId);

    Optional<OrganizationMember> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByAccountIdAndOrganizationId(Long accountId, Long organizationId);

    long countByOrganizationId(Long organizationId);

    long countByOrganizationIdAndRole(Long organizationId, OrganizationRole role);

    boolean existsByOrganizationIdAndId(Long organizationId, Long id);
}
