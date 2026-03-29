import { authorizedFetch } from "@/lib/api/authorized";
import { buildApiUrl, buildApiUrlObject } from "@/lib/api/config";
import { ApiError } from "@/lib/api/errors";
import type {
  AdminActivityAnalyticsResponse,
  AdminTrendResponse,
  ActivityOverviewResponse,
  CanonicalEventTypeAggregateResponse,
  FunnelReportResponse,
  FunnelOptionsResponse,
  RetentionMatrixResponse,
  RouteAggregateResponse,
  UserAnalyticsOverviewResponse,
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
  const url = buildApiUrlObject(`/api/v1/admin/organizations/${organizationId}/analytics/overview`);
  url.searchParams.set("from", from);
  url.searchParams.set("to", to);

  const response = await authorizedFetch(accessToken, url.toString());

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
  const url = buildApiUrlObject(`/api/v1/admin/organizations/${organizationId}/analytics/routes`);
  url.searchParams.set("from", from);
  url.searchParams.set("to", to);
  url.searchParams.set("top", String(top));

  const response = await authorizedFetch(accessToken, url.toString());

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
  const url = buildApiUrlObject(
    `/api/v1/admin/organizations/${organizationId}/analytics/event-types`,
  );
  url.searchParams.set("from", from);
  url.searchParams.set("to", to);
  url.searchParams.set("top", String(top));

  const response = await authorizedFetch(accessToken, url.toString());

  if (!response.ok) {
    throw new ApiError(formatErrorMessage(response.status), response.status);
  }

  return (await response.json()) as CanonicalEventTypeAggregateResponse;
}

export async function fetchTrends(
  accessToken: string,
  organizationId: string,
  from: string,
  to: string,
  bucket: "HOUR" | "DAY",
) {
  const url = buildApiUrlObject(`/api/v1/admin/organizations/${organizationId}/analytics/trends`);
  url.searchParams.set("from", from);
  url.searchParams.set("to", to);
  url.searchParams.set("bucket", bucket);

  const response = await authorizedFetch(accessToken, url.toString());

  if (!response.ok) {
    throw new ApiError(formatErrorMessage(response.status), response.status);
  }

  return (await response.json()) as AdminTrendResponse;
}

export async function fetchUsers(
  accessToken: string,
  organizationId: string,
  from: string,
  to: string,
) {
  const url = buildApiUrlObject(`/api/v1/admin/organizations/${organizationId}/analytics/users`);
  url.searchParams.set("from", from);
  url.searchParams.set("to", to);

  const response = await authorizedFetch(accessToken, url.toString());

  if (!response.ok) {
    throw new ApiError(formatErrorMessage(response.status), response.status);
  }

  return (await response.json()) as UserAnalyticsOverviewResponse;
}

export async function fetchActivity(
  accessToken: string,
  organizationId: string,
  from: string,
  to: string,
) {
  const url = buildApiUrlObject(`/api/v1/admin/organizations/${organizationId}/analytics/activity`);
  url.searchParams.set("from", from);
  url.searchParams.set("to", to);

  const response = await authorizedFetch(accessToken, url.toString());

  if (!response.ok) {
    throw new ApiError(formatErrorMessage(response.status), response.status);
  }

  return (await response.json()) as AdminActivityAnalyticsResponse;
}

export async function fetchRetention(
  accessToken: string,
  organizationId: string,
  from: string,
  to: string,
  days: number[],
  minCohortUsers = 1,
) {
  const url = buildApiUrlObject(`/api/v1/admin/organizations/${organizationId}/analytics/retention`);
  url.searchParams.set("from", from);
  url.searchParams.set("to", to);
  days.forEach((day) => url.searchParams.append("days", String(day)));
  url.searchParams.set("minCohortUsers", String(minCohortUsers));

  const response = await authorizedFetch(accessToken, url.toString());

  if (!response.ok) {
    throw new ApiError(formatErrorMessage(response.status), response.status);
  }

  return (await response.json()) as RetentionMatrixResponse;
}

export async function fetchFunnel(
  accessToken: string,
  organizationId: string,
  payload: {
    from: string;
    to: string;
    conversionWindowDays?: number;
    steps: {
      canonicalEventType: string;
      routeKey?: string | null;
    }[];
  },
) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/analytics/funnels/report`),
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    },
  );

  if (!response.ok) {
    throw new ApiError(formatErrorMessage(response.status), response.status);
  }

  return (await response.json()) as FunnelReportResponse;
}

export async function fetchFunnelOptions(accessToken: string, organizationId: string) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/analytics/funnels/options`),
    {},
  );

  if (!response.ok) {
    throw new ApiError(formatErrorMessage(response.status), response.status);
  }

  return (await response.json()) as FunnelOptionsResponse;
}
