import { ApiError } from "@/lib/api/auth";
import { buildApiUrl } from "@/lib/api/config";
import type {
  ActivityOverviewResponse,
  CanonicalEventTypeAggregateResponse,
  RouteAggregateResponse,
} from "@/types/analytics";

function formatErrorMessage(status: number) {
  if (status === 403) {
    return "이 organization의 analytics를 조회할 권한이 없습니다.";
  }

  if (status === 404) {
    return "요청한 organization을 찾을 수 없습니다.";
  }

  if (status === 400) {
    return "analytics 조회 파라미터가 올바르지 않습니다.";
  }

  return "analytics 데이터를 불러오지 못했습니다.";
}

export async function fetchOverview(
  accessToken: string,
  organizationId: string,
  from: string,
  to: string,
) {
  const url = new URL(
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/analytics/overview`),
  );
  url.searchParams.set("from", from);
  url.searchParams.set("to", to);

  const response = await fetch(url.toString(), {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError(formatErrorMessage(response.status), response.status);
  }

  return (await response.json()) as ActivityOverviewResponse;
}

export async function fetchRoutes(
  accessToken: string,
  organizationId: string,
  from: string,
  to: string,
  top = 30,
) {
  const url = new URL(
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/analytics/routes`),
  );
  url.searchParams.set("from", from);
  url.searchParams.set("to", to);
  url.searchParams.set("top", String(top));

  const response = await fetch(url.toString(), {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError(formatErrorMessage(response.status), response.status);
  }

  return (await response.json()) as RouteAggregateResponse;
}

export async function fetchEventTypes(
  accessToken: string,
  organizationId: string,
  from: string,
  to: string,
  top = 30,
) {
  const url = new URL(
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/analytics/event-types`),
  );
  url.searchParams.set("from", from);
  url.searchParams.set("to", to);
  url.searchParams.set("top", String(top));

  const response = await fetch(url.toString(), {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError(formatErrorMessage(response.status), response.status);
  }

  return (await response.json()) as CanonicalEventTypeAggregateResponse;
}
