import { buildApiUrl } from "@/lib/api/config";
import { ApiError } from "@/lib/api/auth";
import type {
  AdminOrganizationMemberInviteRequest,
  AdminOrganizationMemberListResponse,
  AdminOrganizationMemberResponse,
  AdminOrganizationMemberRoleUpdateRequest,
} from "@/types/members";

async function parseErrorMessage(response: Response) {
  try {
    const data = (await response.json()) as { message?: string };
    return data.message ?? "요청 처리에 실패했습니다.";
  } catch {
    return "요청 처리에 실패했습니다.";
  }
}

export async function fetchOrganizationMembers(
  accessToken: string,
  organizationId: string,
) {
  const response = await fetch(
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/members`),
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );

  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status);
  }

  return (await response.json()) as AdminOrganizationMemberListResponse;
}

export async function inviteOrganizationMemberByLoginId(
  accessToken: string,
  organizationId: string,
  request: AdminOrganizationMemberInviteRequest,
) {
  const response = await fetch(
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/members/by-login-id`),
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status);
  }

  return (await response.json()) as AdminOrganizationMemberResponse;
}

export async function updateOrganizationMemberRole(
  accessToken: string,
  organizationId: string,
  memberId: number,
  request: AdminOrganizationMemberRoleUpdateRequest,
) {
  const response = await fetch(
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/members/${memberId}/role`),
    {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status);
  }

  return (await response.json()) as AdminOrganizationMemberResponse;
}

export async function removeOrganizationMember(
  accessToken: string,
  organizationId: string,
  memberId: number,
) {
  const response = await fetch(
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/members/${memberId}`),
    {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );

  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status);
  }
}
