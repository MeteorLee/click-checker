import { buildApiUrl } from "@/lib/api/config";
import type {
  AdminOrganizationApiKeyMetadataResponse,
  AdminOrganizationApiKeyRotateResponse,
  AdminLoginRequest,
  AdminLoginResponse,
  AdminMeResponse,
  AdminOrganizationCreateRequest,
  AdminOrganizationCreateResponse,
  AdminSignupRequest,
  AdminSignupResponse,
} from "@/types/auth";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
  }
}

async function parseErrorMessage(response: Response) {
  try {
    const data = (await response.json()) as { message?: string };
    return data.message ?? "요청 처리에 실패했습니다.";
  } catch {
    return "요청 처리에 실패했습니다.";
  }
}

export async function login(request: AdminLoginRequest) {
  const response = await fetch(buildApiUrl("/api/v1/admin/auth/login"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const message =
      response.status === 401
        ? "로그인 ID 또는 비밀번호가 올바르지 않습니다."
        : await parseErrorMessage(response);
    throw new ApiError(message, response.status);
  }

  return (await response.json()) as AdminLoginResponse;
}

export async function signup(request: AdminSignupRequest) {
  const response = await fetch(buildApiUrl("/api/v1/admin/auth/signup"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const message =
      response.status === 400
        ? "회원가입 규칙에 맞지 않는 입력입니다."
        : await parseErrorMessage(response);
    throw new ApiError(message, response.status);
  }

  return (await response.json()) as AdminSignupResponse;
}

export async function fetchMe(accessToken: string) {
  const response = await fetch(buildApiUrl("/api/v1/admin/me"), {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status);
  }

  return (await response.json()) as AdminMeResponse;
}

export async function createOrganization(
  accessToken: string,
  request: AdminOrganizationCreateRequest,
) {
  const response = await fetch(buildApiUrl("/api/v1/admin/organizations"), {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const message =
      response.status === 400
        ? "organization 이름 규칙에 맞지 않습니다."
        : await parseErrorMessage(response);
    throw new ApiError(message, response.status);
  }

  return (await response.json()) as AdminOrganizationCreateResponse;
}

export async function fetchOrganizationApiKeyMetadata(
  accessToken: string,
  organizationId: string,
) {
  const response = await fetch(
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/api-key`),
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );

  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status);
  }

  return (await response.json()) as AdminOrganizationApiKeyMetadataResponse;
}

export async function rotateOrganizationApiKey(
  accessToken: string,
  organizationId: string,
) {
  const response = await fetch(
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/api-key/rotate`),
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );

  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status);
  }

  return (await response.json()) as AdminOrganizationApiKeyRotateResponse;
}
