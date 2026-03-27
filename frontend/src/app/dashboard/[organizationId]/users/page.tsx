"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
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
  Select,
  SegmentedControl,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { IconAlertCircle, IconUsers } from "@tabler/icons-react";
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
  memberships: {
    value: string;
    label: string;
    role: string;
  }[];
  range: AppliedRange;
  users: UserAnalyticsOverviewResponse;
};

export default function UsersPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<UsersPageState | null>(null);
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
        const [me, users] = await Promise.all([
          fetchMe(accessToken),
          fetchUsers(accessToken, params.organizationId, appliedRange.from, appliedRange.to),
        ]);

        const currentMembership = me.memberships.find(
          (membership) => String(membership.organizationId) === params.organizationId,
        );

        setData({
          organizationName: currentMembership?.organizationName ?? `Organization ${params.organizationId}`,
          memberships: me.memberships.map((membership) => ({
            value: String(membership.organizationId),
            label: membership.organizationName,
            role: membership.role,
          })),
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

        setErrorMessage(
          error instanceof Error ? error.message : "user 데이터를 불러오지 못했습니다.",
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
          title="Users"
          subtitle="선택된 organization의 사용자 현황을 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Analytics"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">user 데이터를 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Users"
          subtitle="선택된 organization의 사용자 현황을 확인합니다."
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
        title={`${data.organizationName} Users`}
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
                  <Select
                    aria-label="organization 전환"
                    data={data.memberships.map((membership) => ({
                      value: membership.value,
                      label: `${membership.label} · ${membership.role}`,
                    }))}
                    radius="xl"
                    size="sm"
                    value={params.organizationId}
                    w={280}
                    onChange={(value) => {
                      if (value && value !== params.organizationId) {
                        router.push(`/dashboard/${value}/users`);
                      }
                    }}
                  />
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
