import { buildApiUrl } from "@/lib/api/config";
import { ApiError } from "@/lib/api/errors";
import {
  clearAccessToken,
  getRefreshToken,
  setSessionTokens,
} from "@/lib/session/token-store";

type RefreshResponse = {
  accessToken: string;
  refreshToken: string;
};

let refreshPromise: Promise<string | null> | null = null;

async function parseErrorMessage(response: Response) {
  try {
    const data = (await response.json()) as { message?: string };
    return data.message ?? "요청 처리에 실패했습니다.";
  } catch {
    return "요청 처리에 실패했습니다.";
  }
}

async function refreshAccessToken() {
  const refreshToken = getRefreshToken();

  if (!refreshToken) {
    clearAccessToken();
    return null;
  }

  if (!refreshPromise) {
    refreshPromise = (async () => {
      const response = await fetch(buildApiUrl("/api/v1/admin/auth/refresh"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) {
        clearAccessToken();
        throw new ApiError(await parseErrorMessage(response), response.status);
      }

      const result = (await response.json()) as RefreshResponse;
      setSessionTokens(result.accessToken, result.refreshToken);
      return result.accessToken;
    })().finally(() => {
      refreshPromise = null;
    });
  }

  try {
    return await refreshPromise;
  } catch {
    return null;
  }
}

export async function authorizedFetch(
  accessToken: string | null,
  input: string,
  init: RequestInit = {},
) {
  const requestHeaders = new Headers(init.headers ?? {});

  if (accessToken) {
    requestHeaders.set("Authorization", `Bearer ${accessToken}`);
  }

  let response = await fetch(input, {
    ...init,
    headers: requestHeaders,
  });

  if (response.status !== 401) {
    return response;
  }

  const refreshedAccessToken = await refreshAccessToken();
  if (!refreshedAccessToken) {
    return response;
  }

  const retryHeaders = new Headers(init.headers ?? {});
  retryHeaders.set("Authorization", `Bearer ${refreshedAccessToken}`);

  response = await fetch(input, {
    ...init,
    headers: retryHeaders,
  });

  return response;
}
