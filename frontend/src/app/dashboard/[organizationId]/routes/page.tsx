"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { DashboardAccessState } from "@/components/common/dashboard-access-state";
import { ConsoleHeader } from "@/components/common/console-header";
import { fetchMe } from "@/lib/api/auth";
import { fetchRoutes } from "@/lib/api/analytics";
import { getAccessToken } from "@/lib/session/token-store";
import {
  getCustomOverviewRange,
  getInclusiveDateRangeLengthDays,
  getOverviewRange,
  MAX_ANALYTICS_RANGE_DAYS,
} from "@/lib/utils/date";
import { formatNumber } from "@/lib/utils/format";
import type { RouteAggregateResponse } from "@/types/analytics";
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
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { IconAlertCircle, IconSettings } from "@tabler/icons-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

type RangePreset = "1d" | "7d" | "30d" | "custom";

type AppliedRange = {
  from: string;
  to: string;
  displayFrom: string;
  displayTo: string;
};

type RoutesPageState = {
  organizationName: string;
  range: AppliedRange;
  routes: RouteAggregateResponse;
};

export default function RoutesPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<RoutesPageState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedRange, setSelectedRange] = useState<RangePreset>("7d");
  const [appliedRange, setAppliedRange] = useState<AppliedRange>(getOverviewRange(7));
  const [customFrom, setCustomFrom] = useState<string>(getOverviewRange(7).displayFrom);
  const [customTo, setCustomTo] = useState<string>(getOverviewRange(7).displayTo);
  const [rangeValidationMessage, setRangeValidationMessage] = useState<string | null>(null);
  const [isRangePopoverOpened, setIsRangePopoverOpened] = useState(false);

  function getRangeLengthDays(range: AppliedRange) {
    const start = new Date(`${range.displayFrom}T00:00:00`);
    const end = new Date(`${range.displayTo}T00:00:00`);
    return Math.floor((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;
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
        const [me, routes] = await Promise.all([
          fetchMe(accessToken),
          fetchRoutes(accessToken, params.organizationId, appliedRange.from, appliedRange.to),
        ]);

        const currentMembership = me.memberships.find(
          (membership) => String(membership.organizationId) === params.organizationId,
        );

        setData({
          organizationName: currentMembership?.organizationName ?? `Organization ${params.organizationId}`,
          range: appliedRange,
          routes,
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
          error instanceof Error ? error.message : "경로 데이터를 불러오지 못했습니다.",
        );
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [appliedRange, params.organizationId, router]);

  const chartData = useMemo(
    () =>
      data?.routes.items.slice(0, 10).map((item) => ({
        routeKey: item.routeKey,
        count: item.count,
      })) ?? [],
    [data?.routes.items],
  );

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

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="경로 상세"
          subtitle="선택한 조직의 경로 집계를 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Analytics"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">경로 데이터를 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage && (errorStatus === 403 || errorStatus === 404)) {
    return (
      <DashboardAccessState
        title="경로 상세"
        subtitle="선택한 조직의 경로 집계를 확인합니다."
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
          title="경로 상세"
          subtitle="선택한 조직의 경로 집계를 확인합니다."
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
        title={`${data.organizationName} 경로 상세`}
        subtitle="정규화된 route key 기준으로 상위 경로 집계를 확인합니다."
        backHref={`/dashboard/${params.organizationId}`}
        badge="Routes"
      />
      <Container size="xl" pb={72}>
        <Stack gap="xl">
          <Paper radius="32px" p={40} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <div>
                  <Badge color="blue" variant="light" mb="md">
                    Route Aggregate
                  </Badge>
                  <Title order={1}>상위 route 상세</Title>
                  <Text c="dimmed" mt="sm">
                    현재 organization에서 많이 조회된 route를 정규화된 route key 기준으로 보여줍니다.
                  </Text>
                </div>
                <Stack gap="xs" align="flex-end">
                  <Button
                    leftSection={<IconSettings size={16} />}
                    radius="xl"
                    size="sm"
                    variant="default"
                    onClick={() => router.push(`/dashboard/${params.organizationId}/route-templates`)}
                  >
                    Route 규칙 관리
                  </Button>
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
            </Stack>
          </Paper>

          <Paper radius="28px" p="xl" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="center">
                <div>
                  <Text fw={700} size="lg">
                    Top Routes
                  </Text>
                  <Text c="dimmed" size="sm">
                    현재 기간에 수집된 이벤트 수 기준 상위 route입니다.
                  </Text>
                </div>
                <Badge color="dark" radius="xl" variant="light">
                  {data.routes.items.length} routes
                </Badge>
              </Group>

              {data.routes.items.length === 0 ? (
                <Alert color="gray" radius="lg" variant="light">
                  표시할 route 데이터가 없습니다.
                </Alert>
              ) : (
                <Stack gap="lg">
                  <div style={{ width: "100%", height: 360 }}>
                    <ResponsiveContainer>
                      <BarChart
                        data={chartData}
                        layout="vertical"
                        barCategoryGap="22%"
                        barSize={24}
                        margin={{ left: 16, right: 16, top: 6, bottom: 6 }}
                      >
                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.22)" />
                        <XAxis type="number" tick={{ fontSize: 12 }} />
                        <YAxis
                          dataKey="routeKey"
                          type="category"
                          width={156}
                          tick={{ fontSize: 12, fill: "var(--mantine-color-gray-8)" }}
                        />
                        <Tooltip />
                        <Bar
                          dataKey="count"
                          name="이벤트"
                          fill="var(--mantine-color-blue-5)"
                          radius={[0, 8, 8, 0]}
                        />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>

                  <Table highlightOnHover>
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th>순위</Table.Th>
                        <Table.Th>Route</Table.Th>
                        <Table.Th style={{ textAlign: "right" }}>Events</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {data.routes.items.map((item, index) => (
                        <Table.Tr key={`${item.routeKey}-${index}`}>
                          <Table.Td>{index + 1}</Table.Td>
                          <Table.Td>{item.routeKey}</Table.Td>
                          <Table.Td style={{ textAlign: "right" }}>
                            {formatNumber(item.count)}
                          </Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Stack>
              )}
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}
