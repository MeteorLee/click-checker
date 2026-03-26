export type ActivityOverviewComparison = {
  current: number;
  previous: number;
  delta: number;
  deltaRate: number | null;
  hasPreviousBaseline: boolean;
};

export type ActivityOverviewRouteSummary = {
  routeKey: string;
  count: number;
};

export type ActivityOverviewEventTypeSummary = {
  eventType: string;
  count: number;
};

export type ActivityOverviewResponse = {
  organizationId: number;
  externalUserId: string | null;
  from: string;
  to: string;
  eventType: string | null;
  totalEvents: number;
  uniqueUsers: number | null;
  identifiedEventRate: number | null;
  eventTypeMappingCoverage: number | null;
  routeMatchCoverage: number | null;
  comparison: ActivityOverviewComparison;
  topRoutes: ActivityOverviewRouteSummary[];
  topEventTypes: ActivityOverviewEventTypeSummary[];
};

