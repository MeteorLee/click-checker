"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import { fetchMe } from "@/lib/api/auth";
import { fetchFunnel, fetchFunnelOptions } from "@/lib/api/analytics";
import { getAccessToken } from "@/lib/session/token-store";
import {
  getCustomOverviewRange,
  getInclusiveDateRangeLengthDays,
  getOverviewRange,
  MAX_ANALYTICS_RANGE_DAYS,
} from "@/lib/utils/date";
import { formatNumber, formatPercent } from "@/lib/utils/format";
import type { FunnelOptionsResponse, FunnelReportResponse } from "@/types/analytics";
import {
  Alert,
  Badge,
  Button,
  Container,
  Group,
  Loader,
  Paper,
  Popover,
  Progress,
  Select,
  SegmentedControl,
  SimpleGrid,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import {
  IconAlertCircle,
  IconFilter,
  IconPlus,
  IconSparkles,
  IconTrash,
  IconAdjustmentsHorizontal,
} from "@tabler/icons-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

type RangePreset = "7d" | "30d" | "custom";

type AppliedRange = {
  from: string;
  to: string;
  displayFrom: string;
  displayTo: string;
};

type FunnelStepDraft = {
  canonicalEventType: string;
  routeKey: string;
};

type FunnelPageState = {
  organizationName: string;
  memberships: {
    value: string;
    label: string;
    role: string;
  }[];
  range: AppliedRange;
  options: FunnelOptionsResponse;
  funnel: FunnelReportResponse;
};

const DEFAULT_STEPS: FunnelStepDraft[] = [
  { canonicalEventType: "PAGE_VIEW", routeKey: "" },
  { canonicalEventType: "SIGN_UP", routeKey: "" },
  { canonicalEventType: "PURCHASE", routeKey: "" },
];

const CONVERSION_WINDOW_OPTIONS = [
  { value: "7", label: "7일" },
  { value: "14", label: "14일" },
  { value: "30", label: "30일" },
];

export default function FunnelPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<FunnelPageState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedRange, setSelectedRange] = useState<RangePreset>("30d");
  const [appliedRange, setAppliedRange] = useState<AppliedRange>(getOverviewRange(30));
  const [customFrom, setCustomFrom] = useState<string>(getOverviewRange(30).displayFrom);
  const [customTo, setCustomTo] = useState<string>(getOverviewRange(30).displayTo);
  const [rangeValidationMessage, setRangeValidationMessage] = useState<string | null>(null);
  const [isRangePopoverOpened, setIsRangePopoverOpened] = useState(false);
  const [draftSteps, setDraftSteps] = useState<FunnelStepDraft[]>(DEFAULT_STEPS);
  const [appliedSteps, setAppliedSteps] = useState<FunnelStepDraft[]>(DEFAULT_STEPS);
  const [draftConversionWindowDays, setDraftConversionWindowDays] = useState<string>("7");
  const [appliedConversionWindowDays, setAppliedConversionWindowDays] = useState<number>(7);
  const [stepValidationMessage, setStepValidationMessage] = useState<string | null>(null);
  const [expandedRouteSteps, setExpandedRouteSteps] = useState<number[]>([]);

  function getRangeLengthDays(range: AppliedRange) {
    return getInclusiveDateRangeLengthDays(range.displayFrom, range.displayTo);
  }

  function normalizeSteps(steps: FunnelStepDraft[]) {
    return steps.map((step) => ({
      canonicalEventType: step.canonicalEventType.trim(),
      routeKey: step.routeKey.trim() || null,
    }));
  }

  useEffect(() => {
    if (selectedRange === "custom") return;
    const nextRange = getOverviewRange(selectedRange === "7d" ? 7 : 30);
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
        const [me, options, funnel] = await Promise.all([
          fetchMe(accessToken),
          fetchFunnelOptions(accessToken, params.organizationId),
          fetchFunnel(accessToken, params.organizationId, {
            from: appliedRange.from,
            to: appliedRange.to,
            conversionWindowDays: appliedConversionWindowDays,
            steps: normalizeSteps(appliedSteps),
          }),
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
          options,
          funnel,
        });
      } catch (error) {
        const status =
          "status" in (error as object) ? (error as { status?: number }).status : undefined;

        if (status === 401) {
          router.replace("/login");
          return;
        }

        setErrorMessage(
          error instanceof Error ? error.message : "funnel 데이터를 불러오지 못했습니다.",
        );
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [appliedConversionWindowDays, appliedRange, appliedSteps, params.organizationId, router]);

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

  function handleUpdateStep(index: number, field: keyof FunnelStepDraft, value: string) {
    setDraftSteps((current) =>
      current.map((step, stepIndex) =>
        stepIndex === index ? { ...step, [field]: value } : step,
      ),
    );
  }

  function handleAddStep() {
    setDraftSteps((current) =>
      current.length >= 4
        ? current
        : [...current, { canonicalEventType: "", routeKey: "" }],
    );
  }

  function handleRemoveStep(index: number) {
    setExpandedRouteSteps((current) => current.filter((stepIndex) => stepIndex !== index).map((stepIndex) => (stepIndex > index ? stepIndex - 1 : stepIndex)));
    setDraftSteps((current) =>
      current.length <= 2 ? current : current.filter((_, stepIndex) => stepIndex !== index),
    );
  }

  function toggleRouteStep(index: number) {
    setExpandedRouteSteps((current) =>
      current.includes(index)
        ? current.filter((stepIndex) => stepIndex !== index)
        : [...current, index],
    );
  }

  function handleApplyFunnel() {
    const normalized = normalizeSteps(draftSteps);
    const hasBlank = normalized.some((step) => step.canonicalEventType.length === 0);
    if (hasBlank) {
      setStepValidationMessage("각 단계의 canonical event type을 모두 입력하세요.");
      return;
    }

    setAppliedSteps(
      normalized.map((step) => ({
        canonicalEventType: step.canonicalEventType,
        routeKey: step.routeKey ?? "",
      })),
    );
    setAppliedConversionWindowDays(Number(draftConversionWindowDays));
    setStepValidationMessage(null);
  }

  const summary = useMemo(() => {
    if (!data || data.funnel.items.length === 0) return null;
    const firstStep = data.funnel.items[0];
    const lastStep = data.funnel.items[data.funnel.items.length - 1];
    const biggestDropOff = data.funnel.items
      .slice(1)
      .reduce((current, item) => {
        const dropOff = item.dropOffUsersFromPreviousStep ?? 0;
        if (!current || dropOff > (current.dropOffUsersFromPreviousStep ?? 0)) {
          return item;
        }
        return current;
      }, null as (typeof data.funnel.items)[number] | null);

    return {
      firstStepUsers: firstStep.users,
      lastStepUsers: lastStep.users,
      finalConversionRate: lastStep.conversionRateFromFirstStep,
      biggestDropOff,
    };
  }, [data]);

  const canonicalEventTypeOptions = useMemo(
    () => data?.options.canonicalEventTypes.map((value) => ({ value, label: value })) ?? [],
    [data],
  );

  const routeKeyOptions = useMemo(
    () => [
      { value: "", label: "제한 없음" },
      ...((data?.options.routeKeys ?? []).map((value) => ({ value, label: value }))),
    ],
    [data],
  );

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Funnels"
          subtitle="선택된 organization의 전환 흐름을 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Analytics"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">funnel 데이터를 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Funnels"
          subtitle="선택된 organization의 전환 흐름을 확인합니다."
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
        title={`${data.organizationName} Funnels`}
        subtitle="단계별 전환 흐름과 이탈 구간을 같은 기간 기준으로 확인합니다."
        backHref={`/dashboard/${params.organizationId}`}
        badge="Funnels"
      />
      <Container size="xl" pb={72}>
        <Stack gap="xl">
          <Paper radius="32px" p={40} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <div>
                  <Badge color="lime" variant="light" mb="md">
                    Conversion Funnel
                  </Badge>
                  <Title order={1}>전환 퍼널</Title>
                  <Text c="dimmed" mt="sm">
                    단계별 canonical event type을 기준으로 전환율과 이탈 구간을 계산합니다. routeKey를 같이 넣으면 특정 경로 기준 퍼널도 확인할 수 있습니다.
                  </Text>
                </div>
                <Stack gap="xs" align="flex-end">
                  <Popover
                    opened={isRangePopoverOpened}
                    position="bottom-end"
                    shadow="md"
                    width={400}
                    withArrow
                    onChange={setIsRangePopoverOpened}
                  >
                    <Popover.Target>
                      <Button
                        justify="space-between"
                        radius="xl"
                        variant="light"
                        w={280}
                        onClick={() => setIsRangePopoverOpened((opened) => !opened)}
                      >
                        {`${data.range.displayFrom} ~ ${data.range.displayTo} · ${getRangeLengthDays(data.range)}일`}
                      </Button>
                    </Popover.Target>
                    <Popover.Dropdown>
                      <Stack gap="md">
                        <Text fw={700}>기간 선택</Text>
                        <SegmentedControl
                          data={[
                            { label: "7일", value: "7d" },
                            { label: "30일", value: "30d" },
                          ]}
                          radius="xl"
                          value={selectedRange === "custom" ? "30d" : selectedRange}
                          onChange={(value) => setSelectedRange(value as RangePreset)}
                        />
                        {rangeValidationMessage ? (
                          <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
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

              <Paper radius="24px" p="lg" bg="gray.0" className="console-soft-panel">
                <Stack gap="md">
                  <Group justify="space-between" align="center">
                    <div>
                      <Text fw={700}>퍼널 단계 설정</Text>
                      <Text c="dimmed" size="sm">
                        canonical event type 2~4개를 순서대로 고르고, 단계 사이를 몇 일까지 인정할지 전환 허용 기간을 정합니다.
                      </Text>
                    </div>
                    <Select
                      aria-label="전환 허용 기간"
                      data={CONVERSION_WINDOW_OPTIONS}
                      label="전환 허용 기간"
                      radius="xl"
                      size="sm"
                      value={draftConversionWindowDays}
                      w={140}
                      onChange={(value) => value && setDraftConversionWindowDays(value)}
                    />
                  </Group>

                  {draftSteps.map((step, index) => (
                    <Paper key={index} radius="20px" p="md" withBorder>
                      <Stack gap="sm">
                        <Group justify="space-between" align="flex-start">
                          <Select
                            label={`단계 ${index + 1} event type`}
                            placeholder="event type 선택"
                            radius="xl"
                            value={step.canonicalEventType}
                            w={320}
                            searchable
                            data={canonicalEventTypeOptions}
                            onChange={(value) => handleUpdateStep(index, "canonicalEventType", value ?? "")}
                          />
                          <Button
                            color="red"
                            disabled={draftSteps.length <= 2}
                            leftSection={<IconTrash size={16} />}
                            radius="xl"
                            variant="light"
                            onClick={() => handleRemoveStep(index)}
                          >
                            제거
                          </Button>
                        </Group>

                        <Group justify="space-between" align="center">
                          <Text c="dimmed" size="sm">
                            {step.routeKey ? `routeKey 제한: ${step.routeKey}` : "routeKey 제한 없음"}
                          </Text>
                          <Button
                            leftSection={<IconAdjustmentsHorizontal size={16} />}
                            radius="xl"
                            size="xs"
                            variant="subtle"
                            onClick={() => toggleRouteStep(index)}
                          >
                            {expandedRouteSteps.includes(index) ? "세부 조건 닫기" : "세부 조건 추가"}
                          </Button>
                        </Group>

                        {expandedRouteSteps.includes(index) || step.routeKey ? (
                          <Select
                            clearable
                            label="routeKey (선택)"
                            placeholder="routeKey 선택"
                            radius="xl"
                            value={step.routeKey || null}
                            w={260}
                            searchable
                            data={routeKeyOptions.filter((option) => option.value !== "")}
                            onChange={(value) => handleUpdateStep(index, "routeKey", value ?? "")}
                          />
                        ) : null}
                      </Stack>
                    </Paper>
                  ))}

                  <Group justify="space-between">
                    <Group>
                      <Button
                        disabled={draftSteps.length >= 4}
                        leftSection={<IconPlus size={16} />}
                        radius="xl"
                        variant="light"
                        onClick={handleAddStep}
                      >
                        단계 추가
                      </Button>
                      <Text c="dimmed" size="sm">
                        예: PAGE_VIEW → SIGN_UP → PURCHASE
                      </Text>
                    </Group>
                    <Button leftSection={<IconSparkles size={16} />} radius="xl" onClick={handleApplyFunnel}>
                      funnel 적용
                    </Button>
                  </Group>

                  {stepValidationMessage ? (
                    <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
                      {stepValidationMessage}
                    </Alert>
                  ) : null}
                </Stack>
              </Paper>

              <SimpleGrid cols={{ base: 1, md: 2, xl: 4 }} spacing="lg">
                <Paper radius="24px" p="lg" bg="lime.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="lime.8">
                      시작 단계 사용자
                    </Text>
                    <Text fw={800} size="1.6rem">{formatNumber(summary.firstStepUsers)}</Text>
                  </Stack>
                </Paper>
                <Paper radius="24px" p="lg" bg="blue.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="blue.8">
                      최종 단계 사용자
                    </Text>
                    <Text fw={800} size="1.6rem">{formatNumber(summary.lastStepUsers)}</Text>
                  </Stack>
                </Paper>
                <Paper radius="24px" p="lg" bg="violet.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="violet.8">
                      최종 전환율
                    </Text>
                    <Text fw={800} size="1.6rem">{formatPercent(summary.finalConversionRate)}</Text>
                  </Stack>
                </Paper>
                <Paper radius="24px" p="lg" bg="orange.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="orange.8">
                      가장 큰 이탈 구간
                    </Text>
                    <Text fw={800} size="1.1rem">
                      {summary.biggestDropOff
                        ? `${summary.biggestDropOff.stepOrder - 1} → ${summary.biggestDropOff.stepOrder}`
                        : "-"}
                    </Text>
                  </Stack>
                </Paper>
              </SimpleGrid>
            </Stack>
          </Paper>

          <Paper radius="28px" p={28} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <div>
                <Title order={3}>단계별 전환</Title>
                <Text c="dimmed" size="sm" mt={6}>
                  첫 단계 기준 전환율과 직전 단계 기준 전환율을 함께 보여줍니다. routeKey가 비어 있으면 canonical event type만으로 단계를 판정합니다.
                </Text>
              </div>

              <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
                {data.funnel.items.map((item) => (
                  <Paper key={item.stepOrder} radius="24px" p="lg" withBorder>
                    <Stack gap="md">
                      <Group justify="space-between" align="flex-start">
                        <div>
                          <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                            Step {item.stepOrder}
                          </Text>
                          <Title order={4}>{item.step.canonicalEventType}</Title>
                          <Text c="dimmed" size="sm">
                            {item.step.routeKey ? `routeKey ${item.step.routeKey}` : "routeKey 제한 없음"}
                          </Text>
                        </div>
                        <Badge color="lime" variant="light">
                          {formatNumber(item.users)} users
                        </Badge>
                      </Group>

                      <Stack gap={6}>
                        {item.stepOrder === 1 ? (
                          <Text fw={600} size="sm">
                            기준 사용자 {formatNumber(item.users)}명
                          </Text>
                        ) : (
                          <>
                            <Text fw={600} size="sm">
                              첫 단계 대비 {formatPercent(item.conversionRateFromFirstStep)}
                            </Text>
                            <Progress color="lime" radius="xl" size="lg" value={(item.conversionRateFromFirstStep ?? 0) * 100} />
                          </>
                        )}
                      </Stack>

                      {item.previousStepUsers !== null ? (
                        <Stack gap={6}>
                          <Text fw={600} size="sm">
                            직전 단계 대비 {formatPercent(item.conversionRateFromPreviousStep)} · 이탈 {formatNumber(item.dropOffUsersFromPreviousStep ?? 0)}명
                          </Text>
                          <Progress color="blue" radius="xl" size="sm" value={(item.conversionRateFromPreviousStep ?? 0) * 100} />
                        </Stack>
                      ) : null}
                    </Stack>
                  </Paper>
                ))}
              </SimpleGrid>

              <Table highlightOnHover withColumnBorders>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>단계</Table.Th>
                    <Table.Th>event type</Table.Th>
                    <Table.Th>routeKey</Table.Th>
                    <Table.Th>사용자 수</Table.Th>
                    <Table.Th>첫 단계 대비</Table.Th>
                    <Table.Th>직전 단계 대비</Table.Th>
                    <Table.Th>직전 단계 이탈</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {data.funnel.items.map((item) => (
                    <Table.Tr key={item.stepOrder}>
                      <Table.Td>{item.stepOrder}</Table.Td>
                      <Table.Td>{item.step.canonicalEventType}</Table.Td>
                      <Table.Td>{item.step.routeKey ?? "-"}</Table.Td>
                      <Table.Td>{formatNumber(item.users)}</Table.Td>
                      <Table.Td>{item.stepOrder === 1 ? "기준 단계" : formatPercent(item.conversionRateFromFirstStep)}</Table.Td>
                      <Table.Td>{formatPercent(item.conversionRateFromPreviousStep)}</Table.Td>
                      <Table.Td>{item.dropOffUsersFromPreviousStep == null ? "-" : formatNumber(item.dropOffUsersFromPreviousStep)}</Table.Td>
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
