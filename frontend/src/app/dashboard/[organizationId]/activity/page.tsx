"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { DashboardAccessState } from "@/components/common/dashboard-access-state";
import { ConsoleHeader } from "@/components/common/console-header";
import { fetchMe } from "@/lib/api/auth";
import { fetchActivity } from "@/lib/api/analytics";
import { getAccessToken } from "@/lib/session/token-store";
import {
  getCustomOverviewRange,
  getInclusiveDateRangeLengthDays,
  getOverviewRange,
  MAX_ANALYTICS_RANGE_DAYS,
} from "@/lib/utils/date";
import { formatNumber } from "@/lib/utils/format";
import type { AdminActivityAnalyticsResponse } from "@/types/analytics";
import {
  Alert,
  Badge,
  Button,
  Container,
  Group,
  Loader,
  Paper,
  Popover,
  SegmentedControl,
  SimpleGrid,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

type RangePreset = "1d" | "7d" | "30d" | "custom";
type DayTypeMetric = "total" | "average";

type AppliedRange = {
  from: string;
  to: string;
  displayFrom: string;
  displayTo: string;
};

type ActivityPageState = {
  organizationName: string;
  range: AppliedRange;
  activity: AdminActivityAnalyticsResponse;
};

function formatAverage(value: number) {
  return new Intl.NumberFormat("ko-KR", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(value);
}

function formatHourLabel(hourOfDay: number) {
  return `${hourOfDay.toString().padStart(2, "0")}:00`;
}

function formatDayLabel(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    timeZone: "Asia/Seoul",
  }).format(new Date(value));
}

function formatDayOfWeekLabel(dayOfWeek: number) {
  const labels: Record<number, string> = {
    0: "일",
    1: "월",
    2: "화",
    3: "수",
    4: "목",
    5: "금",
    6: "토",
  };
  return labels[dayOfWeek] ?? String(dayOfWeek);
}

export default function ActivityPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<ActivityPageState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedRange, setSelectedRange] = useState<RangePreset>("7d");
  const [appliedRange, setAppliedRange] = useState<AppliedRange>(getOverviewRange(7));
  const [customFrom, setCustomFrom] = useState<string>(getOverviewRange(7).displayFrom);
  const [customTo, setCustomTo] = useState<string>(getOverviewRange(7).displayTo);
  const [rangeValidationMessage, setRangeValidationMessage] = useState<string | null>(null);
  const [isRangePopoverOpened, setIsRangePopoverOpened] = useState(false);
  const [dayTypeMetric, setDayTypeMetric] = useState<DayTypeMetric>("total");

  function getRangeLengthDays(range: AppliedRange) {
    return getInclusiveDateRangeLengthDays(range.displayFrom, range.displayTo);
  }

  function resolveRangeDays(rangePreset: RangePreset) {
    if (rangePreset === "1d") return 1;
    if (rangePreset === "30d") return 30;
    return 7;
  }

  useEffect(() => {
    if (selectedRange === "custom") return;
    const nextRange = getOverviewRange(resolveRangeDays(selectedRange));
    setAppliedRange(nextRange);
    setCustomFrom(nextRange.displayFrom);
    setCustomTo(nextRange.displayTo);
    setRangeValidationMessage(null);
  }, [selectedRange]);

  useEffect(() => {
    async function load() {
      const accessToken = getAccessToken();
      if (!accessToken) {
        router.replace("/login");
        return;
      }

      setIsLoading(true);
      setErrorMessage(null);
      setErrorStatus(null);

      try {
        const [me, activity] = await Promise.all([
          fetchMe(accessToken),
          fetchActivity(accessToken, params.organizationId, appliedRange.from, appliedRange.to),
        ]);

        const currentMembership = me.memberships.find(
          (membership) => String(membership.organizationId) === params.organizationId,
        );

        setData({
          organizationName: currentMembership?.organizationName ?? `Organization ${params.organizationId}`,
          range: appliedRange,
          activity,
        });
      } catch (error) {
        const status =
          "status" in (error as object) ? (error as { status?: number }).status : undefined;

        if (status === 401) {
          router.replace("/login");
          return;
        }

        setErrorStatus(status ?? null);
        setErrorMessage(
          error instanceof Error ? error.message : "활동량 데이터를 불러오지 못했습니다.",
        );
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [appliedRange, params.organizationId, router]);

  function handleApplyCustomRange() {
    if (!customFrom || !customTo) {
      setRangeValidationMessage("시작일과 종료일을 모두 선택하세요.");
      return;
    }
    if (customFrom > customTo) {
      setRangeValidationMessage("시작일은 종료일보다 늦을 수 없습니다.");
      return;
    }

    const rangeLengthDays = getInclusiveDateRangeLengthDays(customFrom, customTo);
    if (rangeLengthDays > MAX_ANALYTICS_RANGE_DAYS) {
      setRangeValidationMessage(`조회 기간은 최대 ${MAX_ANALYTICS_RANGE_DAYS}일까지 선택할 수 있습니다.`);
      return;
    }

    const nextRange = getCustomOverviewRange(customFrom, customTo);
    setSelectedRange("custom");
    setAppliedRange(nextRange);
    setRangeValidationMessage(null);
    setIsRangePopoverOpened(false);
  }

  const hourlyChartData = useMemo(
    () =>
      data?.activity.weekdayHourlyDistribution.map((item, index) => ({
        label: formatHourLabel(item.hourOfDay),
        weekdayEvents: item.eventCount,
        weekendEvents: data.activity.weekendHourlyDistribution[index]?.eventCount ?? 0,
      })) ?? [],
    [data],
  );

  const dayTypeComparisonData = useMemo(
    () =>
      data
        ? [
            {
              label: "평일",
              eventCount: data.activity.weekdaySummary.eventCount,
              uniqueUserCount: data.activity.weekdaySummary.uniqueUserCount,
              averageEventsPerDay: data.activity.weekdaySummary.averageEventsPerDay,
              averageUniqueUsersPerDay: data.activity.weekdaySummary.averageUniqueUsersPerDay,
            },
            {
              label: "주말",
              eventCount: data.activity.weekendSummary.eventCount,
              uniqueUserCount: data.activity.weekendSummary.uniqueUserCount,
              averageEventsPerDay: data.activity.weekendSummary.averageEventsPerDay,
              averageUniqueUsersPerDay: data.activity.weekendSummary.averageUniqueUsersPerDay,
            },
          ]
        : [],
    [data],
  );

  const dayOfWeekChartData = useMemo(
    () =>
      data?.activity.dayOfWeekDistribution.map((item) => ({
        label: formatDayOfWeekLabel(item.dayOfWeek),
        eventCount: item.eventCount,
        uniqueUserCount: item.uniqueUserCount,
      })) ?? [],
    [data],
  );

  const dayOfWeekSummary = useMemo(() => {
    if (dayOfWeekChartData.length === 0) {
      return null;
    }

    const busiestDay = dayOfWeekChartData.reduce((max, item) =>
      item.eventCount > max.eventCount ? item : max,
    );
    const quietestDay = dayOfWeekChartData.reduce((min, item) =>
      item.eventCount < min.eventCount ? item : min,
    );

    return { busiestDay, quietestDay };
  }, [dayOfWeekChartData]);

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="활동량"
          subtitle="선택한 조직의 활동량을 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Analytics"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">활동량 데이터를 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage && (errorStatus === 403 || errorStatus === 404)) {
    return (
      <DashboardAccessState
        title="활동량"
        subtitle="선택한 조직의 활동량과 분포를 확인합니다."
        backHref={`/dashboard/${params.organizationId}`}
        badge="Analytics"
        status={errorStatus}
        message={errorMessage}
      />
    );
  }

  if (errorMessage) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="활동량"
          subtitle="선택한 조직의 활동량과 분포를 확인합니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Analytics"
        />
        <Container size="md" py={96}>
          <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
            {errorMessage}
          </Alert>
        </Container>
      </ConsoleFrame>
    );
  }

  if (!data) return null;

  return (
    <ConsoleFrame>
      <ConsoleHeader
        title={`${data.organizationName} 활동량`}
        subtitle="선택한 기간 안에서 얼마나 활발했는지, 어느 날과 시간대에 몰렸는지 확인합니다."
        backHref={`/dashboard/${params.organizationId}`}
        badge="Activity"
      />
      <Container size="xl" pb={72}>
        <Stack gap="xl">
          <Paper radius="32px" p={40} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <div>
                  <Badge color="orange" variant="light" mb="md">
                    Activity Distribution
                  </Badge>
                  <Title order={1}>활동 패턴과 분포</Title>
                  <Text c="dimmed" mt="sm">
                    평일과 주말, 요일별 활동량, 시간대 분포를 함께 비교해 활동이 어디에 몰리는지 확인합니다.
                  </Text>
                </div>
                <Stack gap="xs" align="flex-end">
                  <Text c="dimmed" fw={600} size="sm">
                    조회 기간
                  </Text>
                  <Popover
                    opened={isRangePopoverOpened}
                    position="bottom-end"
                    shadow="md"
                    width={420}
                    withArrow
                    onChange={setIsRangePopoverOpened}
                  >
                    <Popover.Target>
                      <Button
                        radius="xl"
                        variant="light"
                        onClick={() => setIsRangePopoverOpened((opened) => !opened)}
                      >
                        {selectedRange === "custom"
                          ? `${data.range.displayFrom} ~ ${data.range.displayTo} · ${getRangeLengthDays(data.range)}일`
                          : selectedRange === "1d"
                            ? "오늘"
                            : selectedRange === "30d"
                              ? "최근 30일"
                              : "최근 7일"}
                      </Button>
                    </Popover.Target>
                    <Popover.Dropdown>
                      <Stack gap="md">
                        <div>
                          <Text fw={700}>조회 기간 설정</Text>
                          <Text c="dimmed" size="sm">
                            현재 적용 중인 기간은 {data.range.displayFrom}부터 {data.range.displayTo}
                            까지, 총 {getRangeLengthDays(data.range)}일입니다.
                          </Text>
                        </div>
                        <SegmentedControl
                          data={[
                            { label: "1일", value: "1d" },
                            { label: "7일", value: "7d" },
                            { label: "30일", value: "30d" },
                          ]}
                          radius="xl"
                          value={selectedRange === "custom" ? "7d" : selectedRange}
                          onChange={(value) => setSelectedRange(value as RangePreset)}
                        />
                        {rangeValidationMessage ? (
                          <Alert
                            color="red"
                            icon={<IconAlertCircle size={18} />}
                            radius="lg"
                            variant="light"
                          >
                            {rangeValidationMessage}
                          </Alert>
                        ) : null}
                        <Group align="flex-end" wrap="wrap">
                          <TextInput
                            label="시작일"
                            radius="xl"
                            type="date"
                            value={customFrom}
                            onChange={(event) => setCustomFrom(event.currentTarget.value)}
                          />
                          <TextInput
                            label="종료일"
                            radius="xl"
                            type="date"
                            value={customTo}
                            onChange={(event) => setCustomTo(event.currentTarget.value)}
                          />
                          <Button radius="xl" onClick={handleApplyCustomRange}>
                            적용
                          </Button>
                        </Group>
                        <Text c="dimmed" size="sm">
                          종료일은 포함해서 계산합니다.
                        </Text>
                      </Stack>
                    </Popover.Dropdown>
                  </Popover>
                </Stack>
              </Group>

              <SimpleGrid cols={{ base: 1, md: 2, xl: 4 }} spacing="lg">
                <Paper radius="24px" p="lg" bg="orange.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="orange.8">
                      총 이벤트
                    </Text>
                    <Text fw={800} size="1.6rem">{formatNumber(data.activity.totalEvents)}</Text>
                  </Stack>
                </Paper>
                <Paper radius="24px" p="lg" bg="yellow.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="yellow.9">
                      일평균 이벤트
                    </Text>
                    <Text fw={800} size="1.6rem">{formatAverage(data.activity.averageEventsPerDay)}</Text>
                  </Stack>
                </Paper>
                <Paper radius="24px" p="lg" bg="teal.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="teal.8">
                      활동이 있었던 일
                    </Text>
                    <Text fw={800} size="1.6rem">{formatNumber(data.activity.activeDays)}일</Text>
                  </Stack>
                </Paper>
                <Paper radius="24px" p="lg" bg="gray.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="dark.6">
                      가장 바빴던 날
                    </Text>
                    <Text fw={800} size="1.2rem">{formatDayLabel(data.activity.peakDayBucketStart)}</Text>
                    <Text c="dimmed" size="sm">{formatNumber(data.activity.peakDayEventCount)} events</Text>
                  </Stack>
                </Paper>
              </SimpleGrid>
            </Stack>
          </Paper>

          <SimpleGrid cols={{ base: 1, xl: 2 }} spacing="lg">
            <Paper radius="28px" p={28} shadow="sm" withBorder className="console-panel">
              <Stack gap="lg">
                <div>
                  <Title order={3}>평일 vs 주말</Title>
                  <Text c="dimmed" size="sm" mt={6}>
                    한국 시간 기준으로 평일과 주말의 총 이벤트, 고유 사용자 수, 일평균 이벤트를 비교합니다.
                  </Text>
                </div>
                <Group justify="flex-end">
                  <SegmentedControl
                    data={[
                      { label: "총량", value: "total" },
                      { label: "일평균", value: "average" },
                    ]}
                    radius="xl"
                    size="sm"
                    value={dayTypeMetric}
                    onChange={(value) => setDayTypeMetric(value as DayTypeMetric)}
                  />
                </Group>

                <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
                  <Paper radius="22px" p="lg" bg="blue.0" className="console-soft-panel">
                    <Stack gap="sm">
                      <Group justify="space-between" align="center">
                        <Text fw={700} size="sm" c="blue.8">
                          평일
                        </Text>
                        <Badge color="blue" radius="xl" variant="light">
                          {dayTypeMetric === "total" ? "총량 기준" : "일평균 기준"}
                        </Badge>
                      </Group>
                      <Stack gap={2}>
                        <Text c="dimmed" fw={600} size="xs">
                          {dayTypeMetric === "total" ? "총 이벤트" : "일평균 이벤트"}
                        </Text>
                        <Text fw={800} size="1.7rem">
                          {dayTypeMetric === "total"
                            ? `${formatNumber(data.activity.weekdaySummary.eventCount)}`
                            : formatAverage(data.activity.weekdaySummary.averageEventsPerDay)}
                        </Text>
                        <Text c="dark.6" fw={700} size="sm">
                          {dayTypeMetric === "total"
                            ? `고유 사용자 ${formatNumber(data.activity.weekdaySummary.uniqueUserCount)}명`
                            : `일평균 고유 사용자 ${formatAverage(data.activity.weekdaySummary.averageUniqueUsersPerDay)}명`}
                        </Text>
                      </Stack>
                    </Stack>
                  </Paper>
                  <Paper radius="22px" p="lg" bg="teal.0" className="console-soft-panel">
                    <Stack gap="sm">
                      <Group justify="space-between" align="center">
                        <Text fw={700} size="sm" c="teal.8">
                          주말
                        </Text>
                        <Badge color="teal" radius="xl" variant="light">
                          {dayTypeMetric === "total" ? "총량 기준" : "일평균 기준"}
                        </Badge>
                      </Group>
                      <Stack gap={2}>
                        <Text c="dimmed" fw={600} size="xs">
                          {dayTypeMetric === "total" ? "총 이벤트" : "일평균 이벤트"}
                        </Text>
                        <Text fw={800} size="1.7rem">
                          {dayTypeMetric === "total"
                            ? `${formatNumber(data.activity.weekendSummary.eventCount)}`
                            : formatAverage(data.activity.weekendSummary.averageEventsPerDay)}
                        </Text>
                        <Text c="dark.6" fw={700} size="sm">
                          {dayTypeMetric === "total"
                            ? `고유 사용자 ${formatNumber(data.activity.weekendSummary.uniqueUserCount)}명`
                            : `일평균 고유 사용자 ${formatAverage(data.activity.weekendSummary.averageUniqueUsersPerDay)}명`}
                        </Text>
                      </Stack>
                    </Stack>
                  </Paper>
                </SimpleGrid>

                <div style={{ width: "100%", height: 280 }}>
                  <ResponsiveContainer>
                    <BarChart data={dayTypeComparisonData} barCategoryGap="22%">
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.22)" />
                      <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                      <YAxis tick={{ fontSize: 12 }} />
                      <Tooltip />
                      <Legend />
                      <Bar
                        dataKey={dayTypeMetric === "total" ? "eventCount" : "averageEventsPerDay"}
                        name={dayTypeMetric === "total" ? "이벤트 수" : "일평균 이벤트"}
                        fill="var(--mantine-color-orange-5)"
                        radius={[8, 8, 0, 0]}
                        barSize={24}
                      />
                      <Bar
                        dataKey={dayTypeMetric === "total" ? "uniqueUserCount" : "averageUniqueUsersPerDay"}
                        name={dayTypeMetric === "total" ? "고유 사용자 수" : "일평균 고유 사용자"}
                        fill="var(--mantine-color-blue-5)"
                        radius={[8, 8, 0, 0]}
                        barSize={24}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </Stack>
            </Paper>

            <Paper radius="28px" p={28} shadow="sm" withBorder className="console-panel">
              <Stack gap="lg">
                <div>
                  <Title order={3}>요일별 활동</Title>
                  <Text c="dimmed" size="sm" mt={6}>
                    어느 요일에 활동이 몰리는지 이벤트 수와 고유 사용자 수를 함께 보여줍니다.
                  </Text>
                </div>

                {dayOfWeekSummary ? (
                  <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
                    <Paper radius="20px" p="md" bg="orange.0" className="console-soft-panel">
                      <Stack gap={2}>
                        <Text fw={700} size="sm" c="orange.8">
                          가장 활발한 요일
                        </Text>
                        <Text fw={800} size="1.2rem">
                          {dayOfWeekSummary.busiestDay.label}
                        </Text>
                        <Text c="dimmed" size="sm">
                          {formatNumber(dayOfWeekSummary.busiestDay.eventCount)} events
                        </Text>
                      </Stack>
                    </Paper>
                    <Paper radius="20px" p="md" bg="gray.0" className="console-soft-panel">
                      <Stack gap={2}>
                        <Text fw={700} size="sm" c="gray.8">
                          가장 조용한 요일
                        </Text>
                        <Text fw={800} size="1.2rem">
                          {dayOfWeekSummary.quietestDay.label}
                        </Text>
                        <Text c="dimmed" size="sm">
                          {formatNumber(dayOfWeekSummary.quietestDay.eventCount)} events
                        </Text>
                      </Stack>
                    </Paper>
                  </SimpleGrid>
                ) : null}

                <div style={{ width: "100%", height: 320 }}>
                  <ResponsiveContainer>
                    <BarChart data={dayOfWeekChartData} barCategoryGap="18%">
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.22)" />
                      <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                      <YAxis tick={{ fontSize: 12 }} />
                      <Tooltip />
                      <Legend />
                      <Bar
                        dataKey="eventCount"
                        name="이벤트 수"
                        fill="var(--mantine-color-orange-5)"
                        radius={[8, 8, 0, 0]}
                        barSize={20}
                      />
                      <Bar
                        dataKey="uniqueUserCount"
                        name="고유 사용자 수"
                        fill="var(--mantine-color-teal-5)"
                        radius={[8, 8, 0, 0]}
                        barSize={20}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </Stack>
            </Paper>

            <Paper radius="28px" p={28} shadow="sm" withBorder className="console-panel">
              <Stack gap="lg">
                <div>
                  <Title order={3}>일별 활동 표</Title>
                  <Text c="dimmed" size="sm" mt={6}>
                    추이 화면과 역할이 겹치지 않도록, 날짜별 값은 표로만 간단히 확인합니다.
                  </Text>
                </div>

                <Table highlightOnHover withColumnBorders>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>날짜</Table.Th>
                      <Table.Th>이벤트</Table.Th>
                      <Table.Th>고유 사용자</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {data.activity.dailyActivity.map((item) => (
                      <Table.Tr key={item.bucketStart}>
                        <Table.Td>{formatDayLabel(item.bucketStart)}</Table.Td>
                        <Table.Td>{formatNumber(item.eventCount)}</Table.Td>
                        <Table.Td>{formatNumber(item.uniqueUserCount)}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Stack>
            </Paper>

            <Paper radius="28px" p={28} shadow="sm" withBorder className="console-panel">
              <Stack gap="lg">
                <div>
                  <Title order={3}>평일/주말 시간대 비교</Title>
                  <Text c="dimmed" size="sm" mt={6}>
                    같은 시간대라도 평일과 주말의 활동량이 어떻게 다른지 한국 시간 기준으로 비교합니다.
                  </Text>
                </div>

                <div style={{ width: "100%", height: 320 }}>
                  <ResponsiveContainer>
                    <LineChart data={hourlyChartData}>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.22)" />
                      <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                      <YAxis tick={{ fontSize: 12 }} />
                      <Tooltip />
                      <Legend />
                      <Line
                        type="monotone"
                        dataKey="weekdayEvents"
                        name="평일 이벤트"
                        stroke="var(--mantine-color-blue-6)"
                        strokeWidth={3}
                        dot={false}
                      />
                      <Line
                        type="monotone"
                        dataKey="weekendEvents"
                        name="주말 이벤트"
                        stroke="var(--mantine-color-teal-6)"
                        strokeWidth={3}
                        dot={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </Stack>
            </Paper>
          </SimpleGrid>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}
