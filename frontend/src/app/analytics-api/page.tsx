"use client";

import { GuideFrame } from "@/components/guide/guide-frame";
import { buildApiUrl } from "@/lib/api/config";
import {
  Accordion,
  Badge,
  Code,
  Container,
  Group,
  Paper,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Title,
} from "@mantine/core";

type ApiGuide = {
  name: string;
  method: "GET" | "POST";
  path: string;
  description: string;
  useWhen: string;
  headers: string[];
  paramsTitle: string;
  params: Array<{
    name: string;
    required: string;
    description: string;
  }>;
  responseFields: Array<{
    name: string;
    description: string;
  }>;
  caution?: string;
  requestExample: string;
  responseExample: string;
};

const apiGuides: ApiGuide[] = [
  {
    name: "Overview",
    method: "GET",
    path: "/api/v1/events/analytics/aggregates/overview",
    description: "총 이벤트, 고유 사용자, 상위 경로와 이벤트를 한 번에 확인합니다.",
    useWhen: "가장 먼저 전체 규모와 대표 경로/이벤트를 보고 싶을 때 사용합니다.",
    headers: ["X-API-Key: ck_live_xxx"],
    paramsTitle: "Query Parameters",
    params: [
      { name: "from", required: "필수", description: "조회 시작 시각(UTC ISO-8601)" },
      { name: "to", required: "필수", description: "조회 종료 시각(UTC ISO-8601)" },
      { name: "externalUserId", required: "선택", description: "특정 사용자 기준으로 좁힐 때 사용" },
      { name: "eventType", required: "선택", description: "특정 raw event type만 집계할 때 사용" },
    ],
    responseFields: [
      { name: "totalEvents", description: "조회 기간 안 전체 이벤트 수" },
      { name: "uniqueUsers", description: "식별 가능한 고유 사용자 수" },
      { name: "identifiedEventRate", description: "식별 이벤트 비율" },
      { name: "topRoutes", description: "상위 route key 목록" },
      { name: "topEventTypes", description: "상위 canonical event type 목록" },
    ],
    requestExample: `curl "${buildApiUrl("/api/v1/events/analytics/aggregates/overview")}?from=2026-03-23T00:00:00Z&to=2026-03-30T00:00:00Z" \\
  -H "X-API-Key: ck_live_xxx"`,
    responseExample: `{
  "totalEvents": 128,
  "uniqueUsers": 43,
  "identifiedEventRate": 0.72,
  "topRoutes": [{ "routeKey": "home", "count": 46 }],
  "topEventTypes": [{ "eventType": "PAGE_VIEW", "count": 71 }]
}`,
  },
  {
    name: "Activity",
    method: "GET",
    path: "/api/v1/events/analytics/activity",
    description: "평일/주말 요약, 요일별 분포, 시간대 분포를 확인합니다.",
    useWhen: "언제 활동이 몰리는지, 평일과 주말 패턴이 어떻게 다른지 보고 싶을 때 사용합니다.",
    headers: ["X-API-Key: ck_live_xxx"],
    paramsTitle: "Query Parameters",
    params: [
      { name: "from", required: "필수", description: "조회 시작 시각(UTC ISO-8601)" },
      { name: "to", required: "필수", description: "조회 종료 시각(UTC ISO-8601)" },
      { name: "timezone", required: "선택", description: "요일/시간대 계산 기준. 기본값은 UTC" },
    ],
    responseFields: [
      { name: "weekdaySummary", description: "평일 이벤트 수와 고유 사용자 수" },
      { name: "weekendSummary", description: "주말 이벤트 수와 고유 사용자 수" },
      { name: "dayOfWeekDistribution", description: "요일별 이벤트/고유 사용자 분포" },
      { name: "weekdayHourlyDistribution", description: "평일 시간대별 분포" },
      { name: "weekendHourlyDistribution", description: "주말 시간대별 분포" },
    ],
    caution: "timezone을 지정하지 않으면 UTC 기준으로 계산됩니다. 실제 서비스 기준 시간대를 명시하는 편이 좋습니다.",
    requestExample: `curl "${buildApiUrl("/api/v1/events/analytics/activity")}?from=2026-03-23T00:00:00Z&to=2026-03-30T00:00:00Z&timezone=Asia/Seoul" \\
  -H "X-API-Key: ck_live_xxx"`,
    responseExample: `{
  "totalEvents": 128,
  "averageEventsPerDay": 18.3,
  "weekdaySummary": { "eventCount": 72, "uniqueUserCount": 31 },
  "weekendSummary": { "eventCount": 56, "uniqueUserCount": 24 }
}`,
  },
  {
    name: "Users Overview",
    method: "GET",
    path: "/api/v1/events/analytics/users/overview",
    description: "익명/식별 이벤트, 신규/기존 사용자 구성을 확인합니다.",
    useWhen: "사용자 구성이 익명/식별, 신규/기존 관점에서 어떻게 나뉘는지 볼 때 사용합니다.",
    headers: ["X-API-Key: ck_live_xxx"],
    paramsTitle: "Query Parameters",
    params: [
      { name: "from", required: "필수", description: "조회 시작 시각(UTC ISO-8601)" },
      { name: "to", required: "필수", description: "조회 종료 시각(UTC ISO-8601)" },
      { name: "externalUserId", required: "선택", description: "특정 사용자 기준으로 좁힐 때 사용" },
    ],
    responseFields: [
      { name: "identifiedEvents", description: "externalUserId가 있는 이벤트 수" },
      { name: "anonymousEvents", description: "익명 이벤트 수" },
      { name: "identifiedUsers", description: "식별 가능한 사용자 수" },
      { name: "newUsers", description: "조회 기간 안 처음 등장한 사용자 수" },
      { name: "returningUsers", description: "이전에 존재하던 재방문 사용자 수" },
    ],
    requestExample: `curl "${buildApiUrl("/api/v1/events/analytics/users/overview")}?from=2026-03-23T00:00:00Z&to=2026-03-30T00:00:00Z" \\
  -H "X-API-Key: ck_live_xxx"`,
    responseExample: `{
  "totalEvents": 128,
  "identifiedEvents": 92,
  "anonymousEvents": 36,
  "identifiedUsers": 43,
  "newUsers": 12,
  "returningUsers": 31
}`,
  },
  {
    name: "Retention Matrix",
    method: "GET",
    path: "/api/v1/events/analytics/retention/matrix",
    description: "코호트별 N일 내 재방문 비율을 표 형태로 조회합니다.",
    useWhen: "기준일별 코호트 유지율을 표나 heatmap처럼 비교할 때 사용합니다.",
    headers: ["X-API-Key: ck_live_xxx"],
    paramsTitle: "Query Parameters",
    params: [
      { name: "from", required: "필수", description: "조회 시작 시각(UTC ISO-8601)" },
      { name: "to", required: "필수", description: "조회 종료 시각(UTC ISO-8601)" },
      { name: "timezone", required: "선택", description: "코호트 날짜 계산 기준. 기본값은 UTC" },
      { name: "days", required: "선택", description: "복수 지정 가능. 기본값은 1,7,30" },
      { name: "minCohortUsers", required: "선택", description: "최소 코호트 사용자 수. 기본값은 1" },
      { name: "externalUserId", required: "선택", description: "특정 사용자 기준으로 좁힐 때 사용" },
    ],
    responseFields: [
      { name: "days", description: "비교하는 유지 시점 목록" },
      { name: "items[].cohortDate", description: "기준일 코호트 날짜" },
      { name: "items[].cohortUsers", description: "그 날짜에 처음 들어온 사용자 수" },
      { name: "items[].values[].retentionRate", description: "N일 내 재방문 비율" },
    ],
    caution: "이 유지율은 exact-day가 아니라 N일 내 재방문 기준입니다.",
    requestExample: `curl "${buildApiUrl("/api/v1/events/analytics/retention/matrix")}?from=2026-03-01T00:00:00Z&to=2026-03-30T00:00:00Z&timezone=Asia/Seoul&days=1&days=7&days=14" \\
  -H "X-API-Key: ck_live_xxx"`,
    responseExample: `{
  "days": [1, 7, 14],
  "items": [
    {
      "cohortDate": "2026-03-10",
      "cohortUsers": 25,
      "values": [{ "day": 1, "users": 8, "retentionRate": 0.32 }]
    }
  ]
}`,
  },
  {
    name: "Retention Daily",
    method: "GET",
    path: "/api/v1/events/analytics/retention/daily",
    description: "일별 유지율을 한 줄 흐름으로 볼 때 사용합니다.",
    useWhen: "코호트별 표보다 더 단순하게 일별 유지율 흐름을 보고 싶을 때 사용합니다.",
    headers: ["X-API-Key: ck_live_xxx"],
    paramsTitle: "Query Parameters",
    params: [
      { name: "from", required: "필수", description: "조회 시작 시각(UTC ISO-8601)" },
      { name: "to", required: "필수", description: "조회 종료 시각(UTC ISO-8601)" },
      { name: "timezone", required: "선택", description: "날짜 계산 기준. 기본값은 UTC" },
      { name: "minCohortUsers", required: "선택", description: "최소 코호트 사용자 수. 기본값은 1" },
      { name: "externalUserId", required: "선택", description: "특정 사용자 기준으로 좁힐 때 사용" },
    ],
    responseFields: [
      { name: "items[].cohortDate", description: "기준일 코호트 날짜" },
      { name: "items[].cohortUsers", description: "그 날짜의 신규 사용자 수" },
      { name: "items[].retainedUsers", description: "N일 내 다시 돌아온 사용자 수" },
      { name: "items[].retentionRate", description: "재방문 비율" },
    ],
    requestExample: `curl "${buildApiUrl("/api/v1/events/analytics/retention/daily")}?from=2026-03-01T00:00:00Z&to=2026-03-30T00:00:00Z&timezone=Asia/Seoul" \\
  -H "X-API-Key: ck_live_xxx"`,
    responseExample: `{
  "items": [
    { "cohortDate": "2026-03-10", "cohortUsers": 25, "retainedUsers": 8, "retentionRate": 0.32 }
  ]
}`,
  },
  {
    name: "Funnels Report",
    method: "POST",
    path: "/api/v1/events/analytics/funnels/report",
    description: "단계별 전환율과 이탈을 계산합니다.",
    useWhen: "가입, 구매처럼 순차적인 이벤트 흐름에서 각 단계 전환율을 보고 싶을 때 사용합니다.",
    headers: ["Content-Type: application/json", "X-API-Key: ck_live_xxx"],
    paramsTitle: "Request Body",
    params: [
      { name: "from", required: "필수", description: "조회 시작 시각(UTC ISO-8601)" },
      { name: "to", required: "필수", description: "조회 종료 시각(UTC ISO-8601)" },
      { name: "steps", required: "필수", description: "2개 이상 4개 이하 단계. canonicalEventType은 필수" },
      { name: "conversionWindowDays", required: "선택", description: "전환 허용 기간. 최대 365" },
      { name: "externalUserId", required: "선택", description: "특정 사용자 기준으로 좁힐 때 사용" },
    ],
    responseFields: [
      { name: "items[].stepOrder", description: "단계 순서" },
      { name: "items[].users", description: "해당 단계에 도달한 사용자 수" },
      { name: "items[].conversionRateFromPreviousStep", description: "직전 단계 대비 전환율" },
    ],
    caution: "steps는 2개 이상 4개 이하만 허용됩니다. 각 단계에는 canonicalEventType이 필요합니다.",
    requestExample: `curl -X POST "${buildApiUrl("/api/v1/events/analytics/funnels/report")}" \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: ck_live_xxx" \\
  -d '{
    "from": "2026-03-01T00:00:00Z",
    "to": "2026-03-30T00:00:00Z",
    "conversionWindowDays": 7,
    "steps": [
      { "canonicalEventType": "PAGE_VIEW", "routeKey": "home" },
      { "canonicalEventType": "SIGN_UP" },
      { "canonicalEventType": "PURCHASE" }
    ]
  }'`,
    responseExample: `{
  "items": [
    { "stepOrder": 1, "users": 1200 },
    { "stepOrder": 2, "users": 320, "conversionRateFromPreviousStep": 0.27 }
  ]
}`,
  },
];

const aggregateApis = [
  ["GET /api/v1/events/analytics/aggregates/raw-event-types", "raw event type 상위 집계"],
  ["GET /api/v1/events/analytics/aggregates/event-types", "canonical event type 상위 집계"],
  ["GET /api/v1/events/analytics/aggregates/event-types/unique-users", "event type별 고유 사용자 집계"],
  ["GET /api/v1/events/analytics/aggregates/paths", "raw path 상위 집계"],
  ["GET /api/v1/events/analytics/aggregates/routes", "route key 상위 집계"],
  ["GET /api/v1/events/analytics/aggregates/routes/unmatched-paths", "매칭되지 않은 path 집계"],
  ["GET /api/v1/events/analytics/aggregates/routes/unique-users", "route key별 고유 사용자 집계"],
  ["GET /api/v1/events/analytics/aggregates/route-event-types", "route key + canonical event type 조합 집계"],
] as const;

const trendApis = [
  ["GET /api/v1/events/analytics/aggregates/time-buckets", "전체 시간 버킷 집계"],
  ["GET /api/v1/events/analytics/aggregates/route-time-buckets", "route key별 시간 버킷 집계"],
  ["GET /api/v1/events/analytics/aggregates/event-type-time-buckets", "canonical event type별 시간 버킷 집계"],
  ["GET /api/v1/events/analytics/aggregates/route-event-type-time-buckets", "route key + event type 조합 시간 버킷 집계"],
] as const;

const commonQueryParams = [
  { name: "from", required: "필수", description: "조회 시작 시각(UTC ISO-8601)" },
  { name: "to", required: "필수", description: "조회 종료 시각(UTC ISO-8601)" },
  { name: "externalUserId", required: "선택", description: "특정 사용자 기준으로 좁힐 때 사용" },
  { name: "timezone", required: "선택", description: "시간대 계산이 필요한 API에서 사용. 기본값은 UTC" },
  { name: "eventType", required: "선택", description: "raw event type으로 좁힐 때 사용" },
  { name: "top", required: "선택", description: "상위 N개 제한. aggregate 계열은 기본값 10" },
  { name: "bucket", required: "선택", description: "trend 계열에서 사용. 예: HOUR, DAY" },
] as const;

function ApiSection({ api }: { api: ApiGuide }) {
  return (
    <Accordion.Item value={api.path}>
      <Accordion.Control>
        <Group gap="sm">
          <Badge color={api.method === "GET" ? "blue" : "teal"} radius="xl" variant="light">
            {api.method}
          </Badge>
          <Text fw={700}>{api.name}</Text>
        </Group>
      </Accordion.Control>
      <Accordion.Panel>
        <Stack gap="lg">
          <Stack gap={4}>
            <Code>{api.path}</Code>
            <Text c="dimmed" size="sm">
              {api.description}
            </Text>
          </Stack>

          <Paper bg="blue.0" p="md" radius="20px" withBorder>
            <Stack gap={6}>
              <Text fw={700} size="sm">
                언제 쓰나
              </Text>
              <Text c="dimmed" size="sm">
                {api.useWhen}
              </Text>
            </Stack>
          </Paper>

          <Paper bg="gray.0" p="md" radius="20px" withBorder>
            <Stack gap={6}>
              <Text fw={700} size="sm">
                Headers
              </Text>
              <Code block>{api.headers.join("\n")}</Code>
            </Stack>
          </Paper>

          <Table.ScrollContainer minWidth={680}>
            <Table highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{api.paramsTitle}</Table.Th>
                  <Table.Th>필수 여부</Table.Th>
                  <Table.Th>설명</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {api.params.map((param) => (
                  <Table.Tr key={param.name}>
                    <Table.Td>
                      <Code>{param.name}</Code>
                    </Table.Td>
                    <Table.Td>{param.required}</Table.Td>
                    <Table.Td>{param.description}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>

          <Paper bg="gray.0" p="md" radius="20px" withBorder>
            <Stack gap="sm">
              <Text fw={700} size="sm">
                응답에서 먼저 볼 값
              </Text>
              <Table.ScrollContainer minWidth={640}>
                <Table>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>필드</Table.Th>
                      <Table.Th>설명</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {api.responseFields.map((field) => (
                      <Table.Tr key={field.name}>
                        <Table.Td>
                          <Code>{field.name}</Code>
                        </Table.Td>
                        <Table.Td>{field.description}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Table.ScrollContainer>
            </Stack>
          </Paper>

          {api.caution ? (
            <Paper bg="orange.0" p="md" radius="20px" withBorder>
              <Stack gap={6}>
                <Text fw={700} size="sm">
                  주의할 점
                </Text>
                <Text c="dimmed" size="sm">
                  {api.caution}
                </Text>
              </Stack>
            </Paper>
          ) : null}

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
            <Paper bg="gray.0" p="md" radius="20px" withBorder>
              <Stack gap={6}>
                <Text fw={700} size="sm">
                  Example Request
                </Text>
                <Code block>{api.requestExample}</Code>
              </Stack>
            </Paper>
            <Paper bg="gray.0" p="md" radius="20px" withBorder>
              <Stack gap={6}>
                <Text fw={700} size="sm">
                  Example Response
                </Text>
                <Code block>{api.responseExample}</Code>
              </Stack>
            </Paper>
          </SimpleGrid>
        </Stack>
      </Accordion.Panel>
    </Accordion.Item>
  );
}

export default function AnalyticsApiPage() {
  return (
    <GuideFrame>
      <Container size="xl" py={56}>
        <Stack gap="xl">
          <Paper
            p={{ base: "xl", md: 40 }}
            radius={36}
            shadow="sm"
            withBorder
            style={{
              background:
                "radial-gradient(circle at top right, rgba(59,130,246,0.12), transparent 28%), radial-gradient(circle at top left, rgba(20,184,166,0.10), transparent 24%), linear-gradient(180deg, rgba(255,255,255,0.98), rgba(248,250,252,0.98))",
            }}
          >
            <Stack gap="lg">
              <Badge color="blue" radius="xl" variant="light" w="fit-content">
                집계 API
              </Badge>
              <Title order={1} style={{ fontSize: "clamp(2rem, 5vw, 3.2rem)", lineHeight: 1.08 }}>
                제품 API로 조회할 수 있는
                <br />
                집계 결과를 정리했습니다.
              </Title>
              <Text c="dimmed" maw={760} size="lg">
                overview, activity, users, retention, funnels뿐 아니라 aggregate와 trend 계열까지 현재 공개된 제품 집계 API를 한 페이지에서 볼 수 있습니다.
              </Text>
            </Stack>
          </Paper>

          <SimpleGrid cols={{ base: 1, md: 3 }} spacing="lg">
            <Paper radius="24px" p="lg" withBorder bg="gray.0">
              <Stack gap={6}>
                <Text fw={700} size="sm">
                  가장 먼저 볼 API
                </Text>
                <Text c="dimmed" size="sm">
                  `Overview`로 전체 이벤트 수와 상위 경로/이벤트를 먼저 확인하는 게 가장 쉽습니다.
                </Text>
              </Stack>
            </Paper>
            <Paper radius="24px" p="lg" withBorder bg="gray.0">
              <Stack gap={6}>
                <Text fw={700} size="sm">
                  시간 기준
                </Text>
                <Text c="dimmed" size="sm">
                  from/to는 UTC ISO-8601 기준입니다. 요일/시간대가 중요한 API는 timezone도 같이 넘기는 편이 안전합니다.
                </Text>
              </Stack>
            </Paper>
            <Paper radius="24px" p="lg" withBorder bg="gray.0">
              <Stack gap={6}>
                <Text fw={700} size="sm">
                  필터링
                </Text>
                <Text c="dimmed" size="sm">
                  externalUserId, eventType, top 같은 값으로 특정 사용자나 상위 항목만 좁혀 볼 수 있습니다.
                </Text>
              </Stack>
            </Paper>
          </SimpleGrid>

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Badge color="teal" radius="xl" variant="light" w="fit-content">
                  공통 헤더
                </Badge>
                <Code block>{`X-API-Key: ck_live_xxx`}</Code>
                <Text c="dimmed" size="sm">
                  제품 API는 모두 `X-API-Key` 기준으로 조직을 판별합니다. 집계 API는 별도 JWT가 필요하지 않습니다.
                </Text>
              </Stack>
            </Paper>

            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Badge color="gray" radius="xl" variant="light" w="fit-content">
                  공통 조회 필드
                </Badge>
                <Table.ScrollContainer minWidth={520}>
                  <Table>
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th>필드</Table.Th>
                        <Table.Th>필수 여부</Table.Th>
                        <Table.Th>설명</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {commonQueryParams.map((param) => (
                        <Table.Tr key={param.name}>
                          <Table.Td>
                            <Code>{param.name}</Code>
                          </Table.Td>
                          <Table.Td>{param.required}</Table.Td>
                          <Table.Td>{param.description}</Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Table.ScrollContainer>
              </Stack>
            </Paper>
          </SimpleGrid>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="lg">
              <Stack gap={4}>
                <Badge color="blue" radius="xl" variant="light" w="fit-content">
                  주요 집계 API
                </Badge>
                <Title order={2}>자주 쓰는 집계 API</Title>
              </Stack>
              <Accordion chevronPosition="right" radius="24px" variant="contained">
                {apiGuides.map((api) => (
                  <ApiSection api={api} key={api.path} />
                ))}
              </Accordion>
            </Stack>
          </Paper>

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Stack gap={4}>
                <Badge color="orange" radius="xl" variant="light" w="fit-content">
                  집계 목록
                </Badge>
                  <Title order={3}>Top N / 조합 집계</Title>
                  <Text c="dimmed" size="sm">
                    top, eventType, externalUserId 같은 query로 상위 항목과 조합 집계를 조회합니다.
                  </Text>
                </Stack>
                <Table.ScrollContainer minWidth={680}>
                  <Table highlightOnHover>
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th>경로</Table.Th>
                        <Table.Th>용도</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {aggregateApis.map(([path, description]) => (
                        <Table.Tr key={path}>
                          <Table.Td>
                            <Code>{path}</Code>
                          </Table.Td>
                          <Table.Td>{description}</Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Table.ScrollContainer>
              </Stack>
            </Paper>

            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Stack gap={4}>
                <Badge color="teal" radius="xl" variant="light" w="fit-content">
                  추이 API
                </Badge>
                  <Title order={3}>시간 버킷 집계</Title>
                  <Text c="dimmed" size="sm">
                    trend 계열은 `bucket`과 `timezone`이 중요합니다. 버킷 수가 너무 많으면 400이 발생할 수 있습니다.
                  </Text>
                </Stack>
                <Table.ScrollContainer minWidth={680}>
                  <Table highlightOnHover>
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th>경로</Table.Th>
                        <Table.Th>용도</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {trendApis.map(([path, description]) => (
                        <Table.Tr key={path}>
                          <Table.Td>
                            <Code>{path}</Code>
                          </Table.Td>
                          <Table.Td>{description}</Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Table.ScrollContainer>
              </Stack>
            </Paper>
          </SimpleGrid>
        </Stack>
      </Container>
    </GuideFrame>
  );
}
