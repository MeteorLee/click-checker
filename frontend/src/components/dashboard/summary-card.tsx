import { Card, List, Stack, Text } from "@mantine/core";

type SummaryItem = {
  label: string;
  value: string;
};

type SummaryCardProps = {
  title: string;
  description: string;
  items: SummaryItem[];
  emptyMessage: string;
};

export function SummaryCard({
  title,
  description,
  items,
  emptyMessage,
}: SummaryCardProps) {
  return (
    <Card radius="28px" p="xl" shadow="sm" withBorder className="console-panel">
      <Stack gap="lg">
        <Stack gap={4}>
          <Text fw={700} size="lg">
            {title}
          </Text>
          <Text c="dimmed" size="sm">
            {description}
          </Text>
        </Stack>

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
