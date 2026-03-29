"use client";

import { clearAccessToken } from "@/lib/session/token-store";
import {
  ActionIcon,
  Badge,
  Button,
  Container,
  Group,
  Paper,
  Stack,
  Text,
} from "@mantine/core";
import { IconArrowLeft, IconLogout } from "@tabler/icons-react";
import { usePathname, useRouter } from "next/navigation";

type ConsoleHeaderProps = {
  title: string;
  subtitle: string;
  backHref?: string;
  badge?: string;
};

export function ConsoleHeader({
  title,
  subtitle,
  backHref,
  badge = "Click Checker",
}: ConsoleHeaderProps) {
  const router = useRouter();
  const pathname = usePathname();
  const showLogout = pathname !== "/login";

  function handleLogout() {
    clearAccessToken();
    router.push("/login");
  }

  return (
    <Container size="xl" py={24}>
      <Paper radius="xl" p="lg" withBorder shadow="xs" className="console-panel">
        <Group justify="space-between" align="flex-start" wrap="wrap">
          <Group align="flex-start" gap="md">
            {backHref ? (
              <ActionIcon
                aria-label="이전 화면으로 이동"
                color="gray"
                radius="xl"
                size={40}
                variant="light"
                onClick={() => router.push(backHref)}
              >
                <IconArrowLeft size={18} />
              </ActionIcon>
            ) : null}

            <Stack gap={6}>
              <Group gap="sm">
                <Badge color="blue" radius="xl" size="lg" variant="light">
                  {badge}
                </Badge>
                <Text c="dimmed" fw={600} size="sm">
                  Admin Console
                </Text>
              </Group>
              <Text fw={800} size="1.4rem">
                {title}
              </Text>
              <Text c="dimmed" maw={680} size="sm">
                {subtitle}
              </Text>
            </Stack>
          </Group>

          {showLogout ? (
            <Button
              color="dark"
              leftSection={<IconLogout size={16} />}
              radius="xl"
              variant="light"
              onClick={handleLogout}
            >
              로그아웃
            </Button>
          ) : null}
        </Group>
      </Paper>
    </Container>
  );
}

