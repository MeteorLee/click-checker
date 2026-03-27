import { Button, Card, Group, List, Stack, Text } from "@mantine/core";
import Link from "next/link";

type SummaryItem = {
  label: string;
  value: string;
};

type SummaryCardProps = {
  title: string;
  description: string;
  items: SummaryItem[];
  emptyMessage: string;
  actionHref?: string;
  actionLabel?: string;
};

export function SummaryCard({
  title,
  description,
  items,
  emptyMessage,
  actionHref,
  actionLabel,
}: SummaryCardProps) {
  return (
    <Card radius="28px" p="xl" shadow="sm" withBorder className="console-panel">
      <Stack gap="lg">
        <Group justify="space-between" align="flex-start">
          <Stack gap={4}>
            <Text fw={700} size="lg">
              {title}
            </Text>
            <Text c="dimmed" size="sm">
              {description}
            </Text>
          </Stack>
          {actionHref && actionLabel ? (
            <Button component={Link} href={actionHref} radius="xl" size="xs" variant="light">
              {actionLabel}
            </Button>
          ) : null}
        </Group>

        {items.length === 0 ? (
          <Text c="dimmed" size="sm">
            {emptyMessage}
          </Text>
        ) : (
          <List spacing="sm" center>
            {items.map((item) => (
              <List.Item key={`${item.label}-${item.value}`}>
                <Text component="span" fw={600}>
                  {item.label}
                </Text>
                <Text component="span" c="dimmed">
                  {" "}
                  · {item.value}
                </Text>
              </List.Item>
            ))}
          </List>
        )}
      </Stack>
    </Card>
  );
}
