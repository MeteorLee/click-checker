"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { DashboardAccessState } from "@/components/common/dashboard-access-state";
import { ConsoleHeader } from "@/components/common/console-header";
import { fetchMe } from "@/lib/api/auth";
import { fetchTrends } from "@/lib/api/analytics";
import { getAccessToken } from "@/lib/session/token-store";
import {
  getCustomOverviewRange,
  getInclusiveDateRangeLengthDays,
  getOverviewRange,
  MAX_ANALYTICS_RANGE_DAYS,
} from "@/lib/utils/date";
import { formatDateTime, formatNumber } from "@/lib/utils/format";
import type { AdminTrendResponse } from "@/types/analytics";
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
type TrendBucket = "HOUR" | "DAY";

type AppliedRange = {
  from: string;
  to: string;
  displayFrom: string;
  displayTo: string;
};

type TrendsPageState = {
  organizationName: string;
  range: AppliedRange;
  trends: AdminTrendResponse;
};

function formatBucketLabel(value: string, bucket: TrendBucket) {
  const date = new Date(value);
  return bucket === "HOUR"
    ? new Intl.DateTimeFormat("ko-KR", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        hour12: false,
        timeZone: "Asia/Seoul",
      }).format(date)
    : new Intl.DateTimeFormat("ko-KR", {
        month: "2-digit",
        day: "2-digit",
        timeZone: "Asia/Seoul",
      }).format(date);
}

function formatSummaryBucketLabel(value: string, bucket: TrendBucket) {
  const date = new Date(value);
  return bucket === "HOUR"
    ? formatDateTime(value)
    : new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        timeZone: "Asia/Seoul",
      }).format(date);
}

export default function TrendsPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<TrendsPageState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedRange, setSelectedRange] = useState<RangePreset>("7d");
  const [selectedBucket, setSelectedBucket] = useState<TrendBucket>("DAY");
  const [appliedRange, setAppliedRange] = useState<AppliedRange>(getOverviewRange(7));
  const [lastDayRange, setLastDayRange] = useState<AppliedRange>(getOverviewRange(7));
  const [lastDayRangePreset, setLastDayRangePreset] = useState<RangePreset>("7d");
  const [customFrom, setCustomFrom] = useState<string>(getOverviewRange(7).displayFrom);
  const [customTo, setCustomTo] = useState<string>(getOverviewRange(7).displayTo);
  const [rangeValidationMessage, setRangeValidationMessage] = useState<string | null>(null);
  const [isRangePopoverOpened, setIsRangePopoverOpened] = useState(false);

  function getRangeLengthDays(range: AppliedRange) {
    return getInclusiveDateRangeLengthDays(range.displayFrom, range.displayTo);
  }

  function resolveRangeDays(rangePreset: RangePreset) {
    if (rangePreset === "1d") return 1;
    if (rangePreset === "30d") return 30;
    return 7;
  }

  function isHourlyAvailable(range: AppliedRange) {
    return getRangeLengthDays(range) === 1;
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
    if (!isHourlyAvailable(appliedRange) && selectedBucket === "HOUR") {
      setSelectedBucket("DAY");
    }
  }, [appliedRange, selectedBucket]);

  function switchBucket(nextBucket: TrendBucket) {
    if (nextBucket === "DAY" && selectedBucket === "HOUR") {
      setSelectedBucket("DAY");
      setSelectedRange(lastDayRangePreset);
      setAppliedRange(lastDayRange);
      setCustomFrom(lastDayRange.displayFrom);
      setCustomTo(lastDayRange.displayTo);
      setRangeValidationMessage(null);
      return;
    }

    if (nextBucket === "HOUR" && !isHourlyAvailable(appliedRange)) {
      setLastDayRange(appliedRange);
      setLastDayRangePreset(selectedRange);
      const targetDate = appliedRange.displayTo;
      const singleDayRange = getCustomOverviewRange(targetDate, targetDate);
      setSelectedRange("custom");
      setAppliedRange(singleDayRange);
      setCustomFrom(targetDate);
      setCustomTo(targetDate);
      setRangeValidationMessage(null);
    }

    setSelectedBucket(nextBucket);
  }

  function getRangeButtonLabel() {
    if (selectedBucket === "HOUR") {
      return `${data?.range.displayFrom ?? appliedRange.displayFrom} 하루`;
    }

    if (selectedRange === "custom") {
      return `${data?.range.displayFrom ?? appliedRange.displayFrom} ~ ${data?.range.displayTo ?? appliedRange.displayTo} · ${getRangeLengthDays(data?.range ?? appliedRange)}일`;
    }

    if (selectedRange === "1d") return "오늘";
    if (selectedRange === "30d") return "최근 30일";
    return "최근 7일";
  }

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
        const [me, trends] = await Promise.all([
          fetchMe(accessToken),
          fetchTrends(accessToken, params.organizationId, appliedRange.from, appliedRange.to, selectedBucket),
        ]);

        const currentMembership = me.memberships.find(
          (membership) => String(membership.organizationId) === params.organizationId,
        );

        setData({
          organizationName: currentMembership?.organizationName ?? `Organization ${params.organizationId}`,
          range: appliedRange,
          trends,
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
          error instanceof Error ? error.message : "추이 데이터를 불러오지 못했습니다.",
        );
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [appliedRange, params.organizationId, router, selectedBucket]);

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

    if (selectedBucket === "HOUR" && rangeLengthDays !== 1) {
      setRangeValidationMessage("시간 단위 추이는 하루만 조회할 수 있습니다.");
      return;
    }

    const nextRange = getCustomOverviewRange(customFrom, customTo);
    setSelectedRange("custom");
    setAppliedRange(nextRange);
    setRangeValidationMessage(null);
    setIsRangePopoverOpened(false);
  }

  const chartData = useMemo(() => {
    if (!data) return [];

    const uniqueUserMap = new Map(
      data.trends.uniqueUserCounts.map((item) => [item.bucketStart, item.count]),
    );

    return data.trends.eventCounts.map((item) => ({
      bucketStart: item.bucketStart,
      label: formatBucketLabel(item.bucketStart, selectedBucket),
      events: item.count,
      uniqueUsers: uniqueUserMap.get(item.bucketStart) ?? 0,
    }));
  }, [data, selectedBucket]);

  const trendSummary = useMemo(() => {
    if (chartData.length === 0) {
      return null;
    }

    const totalEvents = chartData.reduce((sum, item) => sum + item.events, 0);
    const totalUniqueUsers = chartData.reduce((sum, item) => sum + item.uniqueUsers, 0);
    const averageEventsPerBucket = totalEvents / chartData.length;
    const averageUniqueUsersPerBucket = totalUniqueUsers / chartData.length;
    const peakBucket = chartData.reduce((max, item) => (item.events > max.events ? item : max));
    const quietBucket = chartData.reduce((min, item) => (item.events < min.events ? item : min));

    return {
      bucketCount: chartData.length,
      totalEvents,
      averageEventsPerBucket,
      averageUniqueUsersPerBucket,
      peakBucket,
      quietBucket,
    };
  }, [chartData]);

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="추이"
          subtitle="선택한 조직의 시계열 추이를 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Analytics"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">추이 데이터를 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage && (errorStatus === 403 || errorStatus === 404)) {
    return (
      <DashboardAccessState
        title="추이"
        subtitle="선택한 조직의 시계열 추이를 확인합니다."
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
          title="추이"
          subtitle="선택한 조직의 시계열 추이를 확인합니다."
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
        title={`${data.organizationName} 추이`}
        subtitle="이벤트 수와 고유 사용자 수의 시간축 변화를 함께 확인합니다."
        backHref={`/dashboard/${params.organizationId}`}
        badge="Trends"
      />
      <Container size="xl" pb={72}>
        <Stack gap="xl">
          <Paper radius="32px" p={40} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <div>
                  <Badge color="blue" variant="light" mb="md">
                    Time Series
                  </Badge>
                  <Title order={1}>시간축 추이</Title>
                  <Text c="dimmed" mt="sm">
                    기간 안에서 이벤트 수와 고유 사용자 수가 어떻게 움직였는지 선 그래프로 보여줍니다.
                  </Text>
                </div>
                <Stack gap="xs" align="flex-end">
                  <Text c="dimmed" fw={600} size="sm">
                    조회 단위
                  </Text>
                  <SegmentedControl
                    data={[
                      { label: "일 단위로 보기", value: "DAY" },
                      { label: "시간 단위로 보기", value: "HOUR" },
                    ]}
                    radius="xl"
                    value={selectedBucket}
                    onChange={(value) => switchBucket(value as TrendBucket)}
                  />
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
                        {getRangeButtonLabel()}
                      </Button>
                    </Popover.Target>
                    <Popover.Dropdown>
                      <Stack gap="md">
                        <div>
                          <Text fw={700}>조회 기간 설정</Text>
                          {selectedBucket === "HOUR" ? (
                            <Text c="dimmed" size="sm">
                              시간 단위 추이는 하루를 기준으로 보여줍니다. 현재 선택된 날짜는{" "}
                              {data.range.displayFrom}입니다.
                            </Text>
                          ) : (
                            <Text c="dimmed" size="sm">
                              현재 적용 중인 기간은 {data.range.displayFrom}부터 {data.range.displayTo}
                              까지, 총 {getRangeLengthDays(data.range)}일입니다.
                            </Text>
                          )}
                        </div>
                        {selectedBucket === "HOUR" ? (
                          <TextInput
                            label="날짜"
                            radius="xl"
                            type="date"
                            value={customFrom}
                            onChange={(event) => {
                              const value = event.currentTarget.value;
                              setCustomFrom(value);
                              setCustomTo(value);
                            }}
                          />
                        ) : (
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
                        )}
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
                        {selectedBucket === "HOUR" ? (
                          <Group justify="flex-end">
                            <Button radius="xl" onClick={handleApplyCustomRange}>
                              적용
                            </Button>
                          </Group>
                        ) : (
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
                        )}
                        <Text c="dimmed" size="sm">
                          {selectedBucket === "HOUR"
                            ? "선택한 날짜 하루를 시간 단위로 보여줍니다."
                            : "종료일은 포함해서 계산합니다. 시간 단위로 보기를 누르면 현재 종료일 기준 하루 범위로 자동 전환됩니다."}
                        </Text>
                      </Stack>
                    </Popover.Dropdown>
                  </Popover>
                </Stack>
              </Group>
            </Stack>
          </Paper>

          <Paper radius="28px" p="xl" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="center">
                <div>
                  <Text fw={700} size="lg">
                    Event / Unique User Trends
                  </Text>
                  <Text c="dimmed" size="sm">
                    {selectedBucket === "HOUR"
                      ? "선택한 하루를 시간 단위로 나눠 이벤트 수와 고유 사용자 수를 비교합니다."
                      : "선택한 기간을 일 단위로 나눠 이벤트 수와 고유 사용자 수를 비교합니다."}
                  </Text>
                </div>
                <Badge color="dark" radius="xl" variant="light">
                  {chartData.length} buckets
                </Badge>
              </Group>

              {trendSummary ? (
                <SimpleGrid cols={{ base: 1, md: 2, xl: 5 }} spacing="md">
                  <Paper radius="22px" p="lg" bg="blue.0" className="console-soft-panel">
                    <Stack gap={4}>
                      <Text fw={700} size="sm" c="blue.8">
                        총 이벤트
                      </Text>
                      <Text fw={800} size="1.4rem">{formatNumber(trendSummary.totalEvents)}</Text>
                    </Stack>
                  </Paper>
                  <Paper radius="22px" p="lg" bg="teal.0" className="console-soft-panel">
                    <Stack gap={4}>
                      <Text fw={700} size="sm" c="teal.8">
                        버킷당 평균 이벤트
                      </Text>
                      <Text fw={800} size="1.4rem">
                        {formatNumber(Math.round(trendSummary.averageEventsPerBucket))}
                      </Text>
                    </Stack>
                  </Paper>
                  <Paper radius="22px" p="lg" bg="orange.0" className="console-soft-panel">
                    <Stack gap={4}>
                      <Text fw={700} size="sm" c="orange.8">
                        버킷당 평균 고유 사용자
                      </Text>
                      <Text fw={800} size="1.4rem">
                        {formatNumber(Math.round(trendSummary.averageUniqueUsersPerBucket))}
                      </Text>
                    </Stack>
                  </Paper>
                  <Paper radius="22px" p="lg" bg="blue.0" className="console-soft-panel">
                    <Stack gap={4}>
                      <Text fw={700} size="sm" c="blue.8">
                        피크 버킷
                      </Text>
                      <Text fw={800} size="1rem">
                        {formatSummaryBucketLabel(trendSummary.peakBucket.bucketStart, selectedBucket)}
                      </Text>
                      <Text c="dimmed" size="sm">
                        {formatNumber(trendSummary.peakBucket.events)} events
                      </Text>
                    </Stack>
                  </Paper>
                  <Paper radius="22px" p="lg" bg="gray.0" className="console-soft-panel">
                    <Stack gap={4}>
                      <Text fw={700} size="sm" c="gray.8">
                        가장 조용한 버킷
                      </Text>
                      <Text fw={800} size="1rem">
                        {formatSummaryBucketLabel(trendSummary.quietBucket.bucketStart, selectedBucket)}
                      </Text>
                      <Text c="dimmed" size="sm">
                        {formatNumber(trendSummary.quietBucket.events)} events
                      </Text>
                    </Stack>
                  </Paper>
                </SimpleGrid>
              ) : null}

              <div style={{ width: "100%", height: 360 }}>
                <ResponsiveContainer>
                  <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e9ecef" />
                    <XAxis dataKey="label" minTickGap={24} stroke="#6c757d" />
                    <YAxis stroke="#6c757d" />
                    <Tooltip
                      formatter={(value) =>
                        typeof value === "number" ? formatNumber(value) : String(value ?? "-")
                      }
                      labelFormatter={(_, payload) =>
                        payload?.[0]?.payload?.bucketStart
                          ? formatDateTime(payload[0].payload.bucketStart)
                          : "-"
                      }
                    />
                    <Legend />
                    <Line
                      type="monotone"
                      dataKey="events"
                      name="이벤트 수"
                      stroke="#1c7ed6"
                      strokeWidth={3}
                      dot={false}
                    />
                    <Line
                      type="monotone"
                      dataKey="uniqueUsers"
                      name="고유 사용자 수"
                      stroke="#0ca678"
                      strokeWidth={3}
                      dot={false}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </Stack>
          </Paper>

          <Paper radius="28px" p="xl" withBorder className="console-panel">
            <Stack gap="lg">
              <Text fw={700} size="lg">
                현재 버킷 요약
              </Text>
              <Text c="dimmed" size="sm">
                차트에 표시된 {selectedBucket === "HOUR" ? "시간 단위" : "일 단위"} 구간을 같은 기준으로 숫자로 확인합니다.
              </Text>
              <Table highlightOnHover>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Bucket Start</Table.Th>
                    <Table.Th style={{ textAlign: "right" }}>Events</Table.Th>
                    <Table.Th style={{ textAlign: "right" }}>Unique Users</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {chartData.map((item) => (
                    <Table.Tr key={item.bucketStart}>
                      <Table.Td>{formatDateTime(item.bucketStart)}</Table.Td>
                      <Table.Td style={{ textAlign: "right" }}>{formatNumber(item.events)}</Table.Td>
                      <Table.Td style={{ textAlign: "right" }}>{formatNumber(item.uniqueUsers)}</Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}
