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

export type RouteAggregateItem = {
  routeKey: string;
  count: number;
};

export type RouteAggregateResponse = {
  organizationId: number;
  externalUserId: string | null;
  from: string;
  to: string;
  eventType: string | null;
  top: number;
  items: RouteAggregateItem[];
};

export type CanonicalEventTypeAggregateItem = {
  canonicalEventType: string;
  count: number;
};

export type CanonicalEventTypeAggregateResponse = {
  organizationId: number;
  externalUserId: string | null;
  from: string;
  to: string;
  top: number;
  items: CanonicalEventTypeAggregateItem[];
};

export type AdminTrendPointResponse = {
  bucketStart: string;
  count: number;
};

export type AdminTrendResponse = {
  organizationId: number;
  from: string;
  to: string;
  timezone: string;
  bucket: "HOUR" | "DAY";
  eventCounts: AdminTrendPointResponse[];
  uniqueUserCounts: AdminTrendPointResponse[];
};

export type UserAnalyticsOverviewResponse = {
  organizationId: number;
  externalUserId: string | null;
  from: string;
  to: string;
  totalEvents: number;
  identifiedEvents: number;
  anonymousEvents: number;
  identifiedUsers: number;
  newUsers: number;
  returningUsers: number;
  newUserEvents: number;
  returningUserEvents: number;
  avgEventsPerIdentifiedUser: number | null;
};

export type AdminActivityDailyItemResponse = {
  bucketStart: string;
  eventCount: number;
  uniqueUserCount: number;
};

export type AdminActivityHourlyItemResponse = {
  hourOfDay: number;
  eventCount: number;
};

export type AdminActivityDayTypeSummaryResponse = {
  eventCount: number;
  uniqueUserCount: number;
  averageEventsPerDay: number;
  averageUniqueUsersPerDay: number;
};

export type AdminActivityDayOfWeekItemResponse = {
  dayOfWeek: number;
  eventCount: number;
  uniqueUserCount: number;
};

export type AdminActivityAnalyticsResponse = {
  organizationId: number;
  from: string;
  to: string;
  timezone: string;
  totalEvents: number;
  averageEventsPerDay: number;
  activeDays: number;
  peakDayBucketStart: string;
  peakDayEventCount: number;
  weekdaySummary: AdminActivityDayTypeSummaryResponse;
  weekendSummary: AdminActivityDayTypeSummaryResponse;
  dayOfWeekDistribution: AdminActivityDayOfWeekItemResponse[];
  dailyActivity: AdminActivityDailyItemResponse[];
  hourlyDistribution: AdminActivityHourlyItemResponse[];
  weekdayHourlyDistribution: AdminActivityHourlyItemResponse[];
  weekendHourlyDistribution: AdminActivityHourlyItemResponse[];
};

export type RetentionMatrixValue = {
  day: number;
  users: number;
  retentionRate: number | null;
};

export type RetentionMatrixRow = {
  cohortDate: string;
  cohortUsers: number;
  values: RetentionMatrixValue[];
};

export type RetentionMatrixResponse = {
  organizationId: number;
  externalUserId: string | null;
  from: string;
  to: string;
  timezone: string;
  days: number[];
  items: RetentionMatrixRow[];
};

export type FunnelStepDefinition = {
  canonicalEventType: string;
  routeKey: string | null;
};

export type FunnelStepResult = {
  stepOrder: number;
  step: FunnelStepDefinition;
  users: number;
  conversionRateFromFirstStep: number | null;
  previousStepUsers: number | null;
  conversionRateFromPreviousStep: number | null;
  dropOffUsersFromPreviousStep: number | null;
};

export type FunnelReportResponse = {
  organizationId: number;
  externalUserId: string | null;
  from: string;
  to: string;
  steps: FunnelStepDefinition[];
  conversionWindow: string;
  items: FunnelStepResult[];
};

export type FunnelOptionsResponse = {
  canonicalEventTypes: string[];
  routeKeys: string[];
};
