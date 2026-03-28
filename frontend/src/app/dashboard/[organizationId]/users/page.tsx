"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { DashboardAccessState } from "@/components/common/dashboard-access-state";
import { ConsoleHeader } from "@/components/common/console-header";
import { fetchMe } from "@/lib/api/auth";
import { fetchUsers } from "@/lib/api/analytics";
import { getAccessToken } from "@/lib/session/token-store";
import {
  getCustomOverviewRange,
  getInclusiveDateRangeLengthDays,
  getOverviewRange,
  MAX_ANALYTICS_RANGE_DAYS,
} from "@/lib/utils/date";
import { formatNumber } from "@/lib/utils/format";
import type { UserAnalyticsOverviewResponse } from "@/types/analytics";
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
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import {
  IconAlertCircle,
  IconRepeat,
  IconSparkles,
  IconUserQuestion,
  IconUsers,
} from "@tabler/icons-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

type RangePreset = "1d" | "7d" | "30d" | "custom";

type AppliedRange = {
  from: string;
  to: string;
  displayFrom: string;
  displayTo: string;
};

type UsersPageState = {
  organizationName: string;
  range: AppliedRange;
  users: UserAnalyticsOverviewResponse;
};

type DonutChartCardProps = {
  title: string;
  description: string;
  items: {
    label: string;
    value: number;
    color: string;
  }[];
};

function DonutChartCard({ title, description, items }: DonutChartCardProps) {
  const total = items.reduce((sum, item) => sum + item.value, 0);
  let progress = 0;
  const gradientStops = items
    .map((item) => {
      const portion = total === 0 ? 0 : (item.value / total) * 100;
      const start = progress;
      const end = progress + portion;
      progress = end;
      return `${item.color} ${start}% ${end}%`;
    })
    .join(", ");

  return (
    <Paper radius="24px" p="lg" className="console-soft-panel" withBorder>
      <Stack gap="lg">
        <div>
          <Text fw={700} size="md">
            {title}
          </Text>
          <Text c="dimmed" size="sm">
            {description}
          </Text>
        </div>

        <Group align="center" justify="space-between" wrap="nowrap">
          <div
            style={{
              width: 148,
              height: 148,
              minWidth: 148,
              borderRadius: "999px",
              background:
                total === 0
                  ? "var(--mantine-color-gray-2)"
                  : `conic-gradient(${gradientStops})`,
              display: "grid",
              placeItems: "center",
            }}
          >
            <div
              style={{
                width: 92,
                height: 92,
                borderRadius: "999px",
                background: "white",
                display: "grid",
                placeItems: "center",
                textAlign: "center",
              }}
            >
              <div>
                <Text fw={800} size="xl">
                  {formatNumber(total)}
                </Text>
                <Text c="dimmed" size="xs">
                  total
                </Text>
              </div>
            </div>
          </div>

          <Stack gap="sm" style={{ flex: 1 }}>
            {items.map((item) => {
              const rate = total === 0 ? 0 : item.value / total;

              return (
                <Group key={item.label} justify="space-between" wrap="nowrap">
                  <Group gap="sm" wrap="nowrap">
                    <div
                      style={{
                        width: 12,
                        height: 12,
                        borderRadius: "999px",
                        background: item.color,
                      }}
                    />
                    <Text fw={600} size="sm">
                      {item.label}
                    </Text>
                  </Group>
                  <Stack align="flex-end" gap={0}>
                    <Text fw={700} size="sm">
                      {formatNumber(item.value)}
                    </Text>
                    <Text c="dimmed" size="xs">
                      {Math.round(rate * 1000) / 10}%
                    </Text>
                  </Stack>
                </Group>
              );
            })}
          </Stack>
        </Group>
      </Stack>
    </Paper>
  );
}

export default function UsersPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<UsersPageState | null>(null);
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
        const [me, users] = await Promise.all([
          fetchMe(accessToken),
          fetchUsers(accessToken, params.organizationId, appliedRange.from, appliedRange.to),
        ]);

        const currentMembership = me.memberships.find(
          (membership) => String(membership.organizationId) === params.organizationId,
        );

        setData({
          organizationName: currentMembership?.organizationName ?? `Organization ${params.organizationId}`,
          range: appliedRange,
          users,
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
          error instanceof Error ? error.message : "사용자 현황을 불러오지 못했습니다.",
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

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="사용자 현황"
          subtitle="선택한 조직의 사용자 현황을 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Analytics"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">사용자 현황을 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage && (errorStatus === 403 || errorStatus === 404)) {
    return (
      <DashboardAccessState
        title="사용자 현황"
        subtitle="선택한 조직의 사용자 현황을 확인합니다."
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
          title="사용자 현황"
          subtitle="선택한 조직의 사용자 현황을 확인합니다."
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

  const identifiedEventRate =
    data.users.totalEvents === 0 ? null : data.users.identifiedEvents / data.users.totalEvents;
  const newUserRate =
    data.users.identifiedUsers === 0 ? null : data.users.newUsers / data.users.identifiedUsers;

  return (
    <ConsoleFrame>
      <ConsoleHeader
        title={`${data.organizationName} 사용자 현황`}
        subtitle="선택한 기간 안에서 식별된 사용자 현황을 요약합니다."
        backHref={`/dashboard/${params.organizationId}`}
        badge="Users"
      />
      <Container size="xl" pb={72}>
        <Stack gap="xl">
          <Paper radius="32px" p={40} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <div>
                  <Badge color="cyan" variant="light" mb="md">
                    User Overview
                  </Badge>
                  <Title order={1}>사용자 현황</Title>
                  <Text c="dimmed" mt="sm">
                    식별된 사용자 수, 신규/재방문 비중, 사용자당 평균 이벤트 수를 같은 기간 기준으로 확인합니다.
                  </Text>
                </div>
                <Stack gap="xs" align="flex-end">
                  <Button
                    color="violet"
                    leftSection={<IconRepeat size={18} />}
                    radius="xl"
                    size="md"
                    variant="light"
                    onClick={() => router.push(`/dashboard/${params.organizationId}/retention`)}
                  >
                    유지율 보기
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

          <SimpleGrid cols={{ base: 1, md: 2, xl: 4 }} spacing="lg">
            <Paper radius="24px" p="lg" className="console-soft-panel" bg="blue.0">
              <Stack gap={4}>
                <Text fw={700} size="sm" c="blue.8">
                  식별된 사용자 수
                </Text>
                <Group gap="xs" align="center">
                  <IconUsers size={18} />
                  <Text fw={800} size="1.6rem">
                    {formatNumber(data.users.identifiedUsers)}
                  </Text>
                </Group>
              </Stack>
            </Paper>
            <Paper radius="24px" p="lg" className="console-soft-panel" bg="teal.0">
              <Stack gap={4}>
                <Text fw={700} size="sm" c="teal.8">
                  신규 사용자
                </Text>
                <Text fw={800} size="1.6rem">
                  {formatNumber(data.users.newUsers)}
                </Text>
              </Stack>
            </Paper>
            <Paper radius="24px" p="lg" className="console-soft-panel" bg="grape.0">
              <Stack gap={4}>
                <Text fw={700} size="sm" c="grape.8">
                  재방문 사용자
                </Text>
                <Text fw={800} size="1.6rem">
                  {formatNumber(data.users.returningUsers)}
                </Text>
              </Stack>
            </Paper>
            <Paper radius="24px" p="lg" className="console-soft-panel" bg="gray.0">
              <Stack gap={4}>
                <Text fw={700} size="sm" c="dark.6">
                  사용자당 평균 이벤트
                </Text>
                <Text fw={800} size="1.6rem">
                  {data.users.avgEventsPerIdentifiedUser == null
                    ? "-"
                    : data.users.avgEventsPerIdentifiedUser.toFixed(2)}
                </Text>
              </Stack>
            </Paper>
            <Paper radius="24px" p="lg" className="console-soft-panel" bg="teal.0">
              <Stack gap={4}>
                <Text fw={700} size="sm" c="teal.8">
                  식별 이벤트 비율
                </Text>
                <Group gap="xs" align="center">
                  <IconSparkles size={18} />
                  <Text fw={800} size="1.6rem">
                    {identifiedEventRate == null
                      ? "-"
                      : `${Math.round(identifiedEventRate * 1000) / 10}%`}
                  </Text>
                </Group>
              </Stack>
            </Paper>
            <Paper radius="24px" p="lg" className="console-soft-panel" bg="orange.0">
              <Stack gap={4}>
                <Text fw={700} size="sm" c="orange.8">
                  신규 사용자 비율
                </Text>
                <Group gap="xs" align="center">
                  <IconUserQuestion size={18} />
                  <Text fw={800} size="1.6rem">
                    {newUserRate == null ? "-" : `${Math.round(newUserRate * 1000) / 10}%`}
                  </Text>
                </Group>
              </Stack>
            </Paper>
          </SimpleGrid>

          <SimpleGrid cols={{ base: 1, xl: 2 }} spacing="lg">
            <DonutChartCard
              title="이벤트 식별 구성"
              description="전체 이벤트 중 익명 이벤트와 식별 이벤트 비율입니다."
              items={[
                {
                  label: "식별 이벤트",
                  value: data.users.identifiedEvents,
                  color: "var(--mantine-color-blue-5)",
                },
                {
                  label: "익명 이벤트",
                  value: data.users.anonymousEvents,
                  color: "var(--mantine-color-gray-4)",
                },
              ]}
            />
            <DonutChartCard
              title="식별 사용자 구성"
              description="식별된 사용자 안에서 신규 사용자와 기존 사용자 비율입니다."
              items={[
                {
                  label: "신규 사용자",
                  value: data.users.newUsers,
                  color: "var(--mantine-color-orange-5)",
                },
                {
                  label: "기존 사용자",
                  value: data.users.returningUsers,
                  color: "var(--mantine-color-grape-5)",
                },
              ]}
            />
          </SimpleGrid>

          <SimpleGrid cols={{ base: 1, md: 2, xl: 4 }} spacing="lg">
            <Paper radius="24px" p="lg" withBorder>
              <Stack gap={4}>
                <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                  Total Events
                </Text>
                <Text fw={800} size="1.4rem">
                  {formatNumber(data.users.totalEvents)}
                </Text>
              </Stack>
            </Paper>
            <Paper radius="24px" p="lg" withBorder>
              <Stack gap={4}>
                <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                  Anonymous Events
                </Text>
                <Text fw={800} size="1.4rem">
                  {formatNumber(data.users.anonymousEvents)}
                </Text>
              </Stack>
            </Paper>
            <Paper radius="24px" p="lg" withBorder>
              <Stack gap={4}>
                <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                  New User Events
                </Text>
                <Text fw={800} size="1.4rem">
                  {formatNumber(data.users.newUserEvents)}
                </Text>
              </Stack>
            </Paper>
            <Paper radius="24px" p="lg" withBorder>
              <Stack gap={4}>
                <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                  Returning User Events
                </Text>
                <Text fw={800} size="1.4rem">
                  {formatNumber(data.users.returningUserEvents)}
                </Text>
              </Stack>
            </Paper>
          </SimpleGrid>

          <Paper radius="28px" p="xl" withBorder className="console-panel">
            <Stack gap="md">
              <Text fw={700} size="lg">
                해석 가이드
              </Text>
              <Text c="dimmed" size="sm">
                사용자 화면은 retention처럼 코호트를 분석하는 화면이 아니라, 현재 선택한 기간 안에서
                식별된 사용자 현황을 단면으로 보여주는 요약 화면입니다.
              </Text>
              <Text c="dimmed" size="sm">
                신규 사용자는 해당 organization에서 첫 이벤트가 현재 기간 안에 들어온 사용자이고,
                재방문 사용자는 현재 기간 이전에도 기록이 있었던 사용자입니다.
              </Text>
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}
