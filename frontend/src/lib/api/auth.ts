import { buildApiUrl } from "@/lib/api/config";
import type {
  AdminLoginRequest,
  AdminLoginResponse,
  AdminMeResponse,
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

