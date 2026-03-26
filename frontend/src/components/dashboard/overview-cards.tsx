import type { ActivityOverviewResponse } from "@/types/analytics";
import {
  Card,
  Group,
  SimpleGrid,
  Stack,
  Text,
  ThemeIcon,
} from "@mantine/core";
import {
  IconActivityHeartbeat,
  IconChartArcs,
  IconChartBar,
  IconUsers,
} from "@tabler/icons-react";
import {
  formatNumber,
  formatPercent,
  formatSignedNumber,
  formatSignedPercent,
} from "@/lib/utils/format";

type OverviewCardsProps = {
  overview: ActivityOverviewResponse;
};

export function OverviewCards({ overview }: OverviewCardsProps) {
  const items = [
    {
      title: "총 이벤트",
      value: formatNumber(overview.totalEvents),
      hint: `이전 기간 대비 ${formatSignedNumber(overview.comparison.delta)}`,
      icon: <IconChartBar size={18} />,
      color: "blue",
    },
    {
      title: "고유 사용자",
      value: formatNumber(overview.uniqueUsers),
      hint: "기간 내 unique external user 기준",
      icon: <IconUsers size={18} />,
      color: "cyan",
    },
    {
      title: "식별 이벤트 비율",
      value: formatPercent(overview.identifiedEventRate),
      hint: "external user가 식별된 이벤트 비중",
      icon: <IconActivityHeartbeat size={18} />,
      color: "teal",
    },
    {
      title: "이전 기간 대비 변화율",
      value: overview.comparison.hasPreviousBaseline
        ? formatSignedPercent(overview.comparison.deltaRate)
        : "-",
      hint: overview.comparison.hasPreviousBaseline
        ? `현재 ${formatNumber(overview.comparison.current)} / 이전 ${formatNumber(overview.comparison.previous)}`
        : "비교 가능한 이전 기간 데이터 없음",
      icon: <IconChartArcs size={18} />,
      color: "orange",
    },
  ];

  return (
    <SimpleGrid cols={{ base: 1, md: 2, xl: 4 }} spacing="lg">
      {items.map((item) => (
        <Card
          key={item.title}
          radius="28px"
          p="xl"
          shadow="sm"
          withBorder
          className="console-panel"
        >
          <Stack gap="lg">
            <Group justify="space-between" align="flex-start">
              <Text c="dimmed" fw={600} size="sm">
                {item.title}
              </Text>
              <ThemeIcon color={item.color} radius="xl" size={38} variant="light">
                {item.icon}
              </ThemeIcon>
            </Group>

            <Stack gap={4}>
              <Text fw={800} size="2rem" lh={1}>
                {item.value}
              </Text>
              <Text c="dimmed" size="sm">
                {item.hint}
              </Text>
            </Stack>
          </Stack>
        </Card>
      ))}
    </SimpleGrid>
  );
}
