import { authorizedFetch } from "@/lib/api/authorized";
import { buildApiUrl } from "@/lib/api/config";
import { ApiError } from "@/lib/api/errors";
import type {
  EventTypeMappingCreateRequest,
  EventTypeMappingItem,
  EventTypeMappingListResponse,
  EventTypeMappingUpdateRequest,
  RouteTemplateCreateRequest,
  RouteTemplateItem,
  RouteTemplateListResponse,
  RouteTemplateUpdateRequest,
} from "@/types/rules";

async function parseErrorMessage(response: Response) {
  try {
    const data = (await response.json()) as { message?: string };
    return data.message ?? "요청 처리에 실패했습니다.";
  } catch {
    return "요청 처리에 실패했습니다.";
  }
}

async function parseOrThrow<T>(response: Response, fallbackMessage: string) {
  if (!response.ok) {
    const message = await parseErrorMessage(response);
    throw new ApiError(message || fallbackMessage, response.status);
  }

  return (await response.json()) as T;
}

export async function fetchRouteTemplates(accessToken: string, organizationId: string) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/route-templates`),
    {},
  );

  return parseOrThrow<RouteTemplateListResponse>(response, "route template 목록을 불러오지 못했습니다.");
}

export async function createRouteTemplate(
  accessToken: string,
  organizationId: string,
  request: RouteTemplateCreateRequest,
) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/route-templates`),
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  return parseOrThrow<RouteTemplateItem>(response, "route template를 추가하지 못했습니다.");
}

export async function updateRouteTemplate(
  accessToken: string,
  organizationId: string,
  routeTemplateId: number,
  request: RouteTemplateUpdateRequest,
) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/route-templates/${routeTemplateId}`),
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  return parseOrThrow<RouteTemplateItem>(response, "route template를 수정하지 못했습니다.");
}

export async function updateRouteTemplateActive(
  accessToken: string,
  organizationId: string,
  routeTemplateId: number,
  active: boolean,
) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/route-templates/${routeTemplateId}/active`),
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ active }),
    },
  );

  return parseOrThrow<RouteTemplateItem>(response, "route template 활성 상태를 바꾸지 못했습니다.");
}

export async function deleteRouteTemplate(
  accessToken: string,
  organizationId: string,
  routeTemplateId: number,
) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/route-templates/${routeTemplateId}`),
    {
      method: "DELETE",
    },
  );

  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status);
  }
}

export async function fetchEventTypeMappings(accessToken: string, organizationId: string) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/event-type-mappings`),
    {},
  );

  return parseOrThrow<EventTypeMappingListResponse>(
    response,
    "event type mapping 목록을 불러오지 못했습니다.",
  );
}

export async function createEventTypeMapping(
  accessToken: string,
  organizationId: string,
  request: EventTypeMappingCreateRequest,
) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/event-type-mappings`),
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  return parseOrThrow<EventTypeMappingItem>(response, "event type mapping을 추가하지 못했습니다.");
}

export async function updateEventTypeMapping(
  accessToken: string,
  organizationId: string,
  eventTypeMappingId: number,
  request: EventTypeMappingUpdateRequest,
) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/event-type-mappings/${eventTypeMappingId}`),
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  return parseOrThrow<EventTypeMappingItem>(response, "event type mapping을 수정하지 못했습니다.");
}

export async function updateEventTypeMappingActive(
  accessToken: string,
  organizationId: string,
  eventTypeMappingId: number,
  active: boolean,
) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/event-type-mappings/${eventTypeMappingId}/active`),
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ active }),
    },
  );

  return parseOrThrow<EventTypeMappingItem>(
    response,
    "event type mapping 활성 상태를 바꾸지 못했습니다.",
  );
}

export async function deleteEventTypeMapping(
  accessToken: string,
  organizationId: string,
  eventTypeMappingId: number,
) {
  const response = await authorizedFetch(
    accessToken,
    buildApiUrl(`/api/v1/admin/organizations/${organizationId}/event-type-mappings/${eventTypeMappingId}`),
    {
      method: "DELETE",
    },
  );

  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status);
  }
}
