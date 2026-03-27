"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
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
  Box,
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
import { IconAlertCircle, IconBolt } from "@tabler/icons-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

type RangePreset = "1d" | "7d" | "30d" | "custom";

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

export default function ActivityPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<ActivityPageState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
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

        setErrorMessage(
          error instanceof Error ? error.message : "activity 데이터를 불러오지 못했습니다.",
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

  const maxHourlyEventCount = useMemo(() => {
    if (!data) return 0;
    return Math.max(...data.activity.hourlyDistribution.map((item) => item.eventCount), 0);
  }, [data]);

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Activity"
          subtitle="선택된 organization의 활동량을 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Analytics"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">activity 데이터를 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Activity"
          subtitle="선택된 organization의 활동량과 분포를 확인합니다."
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
        title={`${data.organizationName} Activity`}
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
                    Activity Summary
                  </Badge>
                  <Title order={1}>활동량과 분포</Title>
                  <Text c="dimmed" mt="sm">
                    총 이벤트 수, 평균 활동량, 가장 바빴던 날과 시간대 분포를 같은 기간 기준으로 묶어 보여줍니다.
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
                <Paper radius="24px" p="lg" bg="grape.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="grape.8">
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
                  <Title order={3}>일별 활동</Title>
                  <Text c="dimmed" size="sm" mt={6}>
                    선택한 기간 안에서 날짜별 이벤트 수와 고유 사용자 수를 비교합니다.
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
                  <Title order={3}>시간대 분포</Title>
                  <Text c="dimmed" size="sm" mt={6}>
                    선택한 기간 전체를 합쳐 어느 시간대에 활동이 몰렸는지 보여줍니다.
                  </Text>
                </div>

                <Stack gap="sm">
                  {data.activity.hourlyDistribution.map((item) => (
                    <Group key={item.hourOfDay} align="center" wrap="nowrap">
                      <Text fw={700} size="sm" w={52}>
                        {formatHourLabel(item.hourOfDay)}
                      </Text>
                      <Box
                        style={{
                          flex: 1,
                          background: "var(--mantine-color-gray-1)",
                          borderRadius: "999px",
                          overflow: "hidden",
                          height: "14px",
                        }}
                      >
                        <Box
                          style={{
                            width:
                              maxHourlyEventCount === 0
                                ? "0%"
                                : `${(item.eventCount / maxHourlyEventCount) * 100}%`,
                            background: "linear-gradient(90deg, var(--mantine-color-orange-5), var(--mantine-color-grape-5))",
                            height: "100%",
                            borderRadius: "999px",
                          }}
                        />
                      </Box>
                      <Text c="dimmed" fw={600} size="sm" ta="right" w={88}>
                        {formatNumber(item.eventCount)}
                      </Text>
                    </Group>
                  ))}
                </Stack>
              </Stack>
            </Paper>
          </SimpleGrid>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}
