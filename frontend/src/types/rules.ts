export type RouteTemplateItem = {
  id: number;
  template: string;
  routeKey: string;
  priority: number;
  active: boolean;
};

export type RouteTemplateListResponse = {
  items: RouteTemplateItem[];
};

export type RouteTemplateCreateRequest = {
  template: string;
  routeKey: string;
  priority: number;
};

export type RouteTemplateUpdateRequest = RouteTemplateCreateRequest;

export type EventTypeMappingItem = {
  id: number;
  rawEventType: string;
  canonicalEventType: string;
  active: boolean;
};

export type EventTypeMappingListResponse = {
  items: EventTypeMappingItem[];
};

export type EventTypeMappingCreateRequest = {
  rawEventType: string;
  canonicalEventType: string;
};

export type EventTypeMappingUpdateRequest = EventTypeMappingCreateRequest;
