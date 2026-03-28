"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { DashboardAccessState } from "@/components/common/dashboard-access-state";
import { ConsoleHeader } from "@/components/common/console-header";
import { fetchMe } from "@/lib/api/auth";
import { fetchRetention } from "@/lib/api/analytics";
import { getAccessToken } from "@/lib/session/token-store";
import {
  getCustomOverviewRange,
  getInclusiveDateRangeLengthDays,
  getOverviewRange,
  MAX_ANALYTICS_RANGE_DAYS,
} from "@/lib/utils/date";
import { formatNumber, formatPercent } from "@/lib/utils/format";
import type { RetentionMatrixResponse, RetentionMatrixValue } from "@/types/analytics";
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
import { IconAlertCircle, IconCheck, IconRepeat } from "@tabler/icons-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

type RangePreset = "1d" | "7d" | "30d" | "custom";

type AppliedRange = {
  from: string;
  to: string;
  displayFrom: string;
  displayTo: string;
};

type RetentionPageState = {
  organizationName: string;
  range: AppliedRange;
  retention: RetentionMatrixResponse;
};

const DAY_OPTIONS = [1, 7, 14, 30] as const;

function retentionCellStyle(value: number | null) {
  const alpha = value == null ? 0 : Math.max(0.08, Math.min(value, 1)) * 0.9;
  return {
    backgroundColor: `rgba(124, 58, 237, ${alpha})`,
    color: alpha > 0.45 ? "white" : "var(--mantine-color-dark-7)",
    borderRadius: "16px",
    padding: "10px 12px",
    minWidth: "128px",
  };
}

export default function RetentionPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<RetentionPageState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedRange, setSelectedRange] = useState<RangePreset>("30d");
  const [selectedDays, setSelectedDays] = useState<number[]>([1, 7, 14]);
  const [appliedRange, setAppliedRange] = useState<AppliedRange>(getOverviewRange(30));
  const [customFrom, setCustomFrom] = useState<string>(getOverviewRange(30).displayFrom);
  const [customTo, setCustomTo] = useState<string>(getOverviewRange(30).displayTo);
  const [rangeValidationMessage, setRangeValidationMessage] = useState<string | null>(null);
  const [isRangePopoverOpened, setIsRangePopoverOpened] = useState(false);

  function getRangeLengthDays(range: AppliedRange) {
    return getInclusiveDateRangeLengthDays(range.displayFrom, range.displayTo);
  }

  function resolveRangeDays(rangePreset: RangePreset) {
    if (rangePreset === "1d") return 1;
    if (rangePreset === "7d") return 7;
    return 30;
  }

  function toggleDay(day: number) {
    setSelectedDays((current) => {
      const next = current.includes(day)
        ? current.filter((value) => value !== day)
        : [...current, day].sort((a, b) => a - b);
      return next.length === 0 ? current : next;
    });
  }

  function isObservable(cohortDate: string, day: number) {
    const cohort = new Date(`${cohortDate}T00:00:00`);
    const lastVisible = new Date(`${data?.range.displayTo ?? appliedRange.displayTo}T00:00:00`);
    cohort.setDate(cohort.getDate() + day);
    return cohort <= lastVisible;
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
        const [me, retention] = await Promise.all([
          fetchMe(accessToken),
          fetchRetention(accessToken, params.organizationId, appliedRange.from, appliedRange.to, selectedDays),
        ]);

        const currentMembership = me.memberships.find(
          (membership) => String(membership.organizationId) === params.organizationId,
        );

        setData({
          organizationName: currentMembership?.organizationName ?? `Organization ${params.organizationId}`,
          range: appliedRange,
          retention,
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
          error instanceof Error ? error.message : "retention 데이터를 불러오지 못했습니다.",
        );
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [appliedRange, params.organizationId, router, selectedDays]);

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

  const summary = useMemo(() => {
    if (!data) return null;
    const items = data.retention.items;
    const cohorts = items.length;
    const totalCohortUsers = items.reduce((sum, item) => sum + item.cohortUsers, 0);

    const averageRates = new Map<number, number | null>();
    for (const day of data.retention.days) {
      averageRates.set(
        day,
        items.length === 0
          ? null
          : items.reduce((sum, item) => {
              const value = item.values.find((entry) => entry.day === day)?.retentionRate ?? 0;
              return sum + value;
            }, 0) / items.length,
      );
    }

    return {
      cohorts,
      totalCohortUsers,
      averageRates,
    };
  }, [data]);

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Retention"
          subtitle="선택된 organization의 재방문 유지율을 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Analytics"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">retention 데이터를 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage && (errorStatus === 403 || errorStatus === 404)) {
    return (
      <DashboardAccessState
        title="Retention"
        subtitle="선택된 organization의 코호트 유지율을 확인합니다."
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
          title="Retention"
          subtitle="선택된 organization의 코호트 유지율을 확인합니다."
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

  if (!data || !summary) return null;

  return (
    <ConsoleFrame>
      <ConsoleHeader
        title={`${data.organizationName} 기준일별 유지율`}
        subtitle="기준일 코호트마다 사용자가 선택한 기간 안에 다시 돌아왔는지 비교합니다."
        backHref={`/dashboard/${params.organizationId}`}
        badge="Retention"
      />
      <Container size="xl" pb={72}>
        <Stack gap="xl">
          <Paper radius="32px" p={40} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <div>
                  <Badge color="violet" variant="light" mb="md">
                    Cohort by Start Date
                  </Badge>
                  <Title order={1}>기준일별 유지율</Title>
                  <Text c="dimmed" mt="sm">
                    각 기준일에 처음 들어온 사용자 수를 출발점으로 보고, 사용자가 고른 기간 안에 다시 돌아온 비율을 비교합니다.
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
                            ? "1일"
                          : selectedRange === "7d"
                            ? "최근 7일"
                            : "최근 30일"}
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
                          value={selectedRange === "custom" ? "30d" : selectedRange}
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

              <div>
                <Text c="dimmed" fw={600} size="sm" mb="xs">
                  유지 시점 선택
                </Text>
                <Group gap="sm">
                  {DAY_OPTIONS.map((day) => {
                    const active = selectedDays.includes(day);
                    return (
                      <Button
                        key={day}
                        color="violet"
                        leftSection={active ? <IconCheck size={16} /> : <IconRepeat size={16} />}
                        radius="xl"
                        variant={active ? "filled" : "light"}
                        onClick={() => toggleDay(day)}
                      >
                        {day === 1 ? "1일 내 재방문" : `${day}일 내 재방문`}
                      </Button>
                    );
                  })}
                </Group>
              </div>

              <SimpleGrid cols={{ base: 1, md: 2, xl: 2 + Math.min(data.retention.days.length, 2) }} spacing="lg">
                <Paper radius="24px" p="lg" bg="violet.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="violet.8">
                      기준일 수
                    </Text>
                    <Text fw={800} size="1.6rem">{formatNumber(summary.cohorts)}</Text>
                  </Stack>
                </Paper>
                <Paper radius="24px" p="lg" bg="grape.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="grape.8">
                      총 신규 사용자
                    </Text>
                    <Text fw={800} size="1.6rem">{formatNumber(summary.totalCohortUsers)}</Text>
                  </Stack>
                </Paper>
                {data.retention.days.map((day) => (
                  <Paper key={day} radius="24px" p="lg" bg="blue.0" className="console-soft-panel">
                    <Stack gap={4}>
                      <Text fw={700} size="sm" c="blue.8">
                        평균 {day}일 내 재방문율
                      </Text>
                      <Text fw={800} size="1.6rem">{formatPercent(summary.averageRates.get(day) ?? null)}</Text>
                    </Stack>
                  </Paper>
                ))}
              </SimpleGrid>
            </Stack>
          </Paper>

          <Paper radius="28px" p={28} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <div>
                <Title order={3}>기준일 코호트 표</Title>
                <Text c="dimmed" size="sm" mt={6}>
                  각 행은 그 날짜에 처음 들어온 사용자 코호트입니다. 선택한 기간 안에 한 번이라도 다시 돌아온 비율을 표시하고, 아직 관측할 수 없는 구간은 `-`로 표시합니다.
                </Text>
              </div>

              <Table highlightOnHover withColumnBorders>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>기준일</Table.Th>
                    <Table.Th>신규 사용자 수</Table.Th>
                    {data.retention.days.map((day) => (
                      <Table.Th key={day}>{day}일 내 재방문</Table.Th>
                    ))}
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {data.retention.items.length === 0 ? (
                    <Table.Tr>
                      <Table.Td colSpan={2 + data.retention.days.length}>
                        <Text c="dimmed" py="md" ta="center">
                          표시할 retention 코호트가 없습니다.
                        </Text>
                      </Table.Td>
                    </Table.Tr>
                  ) : (
                    data.retention.items.map((item) => (
                      <Table.Tr key={item.cohortDate}>
                        <Table.Td>
                          <Stack gap={0}>
                            <Text fw={700}>{item.cohortDate}</Text>
                            <Text c="dimmed" size="xs">
                              first seen cohort
                            </Text>
                          </Stack>
                        </Table.Td>
                        <Table.Td>{formatNumber(item.cohortUsers)}</Table.Td>
                        {data.retention.days.map((day) => {
                          const value = item.values.find((entry) => entry.day === day) as RetentionMatrixValue | undefined;
                          const observable = isObservable(item.cohortDate, day);

                          return (
                            <Table.Td key={`${item.cohortDate}-${day}`}>
                              {!observable ? (
                                <Text c="dimmed" fw={700} ta="center">
                                  -
                                </Text>
                              ) : (
                                <Stack gap={4} style={retentionCellStyle(value?.retentionRate ?? null)}>
                                  <Text fw={800} size="sm">
                                    {formatPercent(value?.retentionRate ?? null)}
                                  </Text>
                                  <Text size="xs">
                                    {formatNumber(value?.users ?? 0)}명 유지
                                  </Text>
                                </Stack>
                              )}
                            </Table.Td>
                          );
                        })}
                      </Table.Tr>
                    ))
                  )}
                </Table.Tbody>
              </Table>
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}
