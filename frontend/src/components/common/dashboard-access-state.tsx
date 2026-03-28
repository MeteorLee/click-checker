"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import { Alert, Button, Container, Group, Paper, Stack, Text, ThemeIcon, Title } from "@mantine/core";
import { IconAlertCircle, IconArrowRight, IconLock, IconSearchOff } from "@tabler/icons-react";
import Link from "next/link";

type DashboardAccessStateProps = {
  title: string;
  subtitle: string;
  backHref?: string;
  badge?: string;
  status: 403 | 404;
  message?: string | null;
};

export function DashboardAccessState({
  title,
  subtitle,
  backHref,
  badge,
  status,
  message,
}: DashboardAccessStateProps) {
  const isForbidden = status === 403;

  return (
    <ConsoleFrame>
      <ConsoleHeader title={title} subtitle={subtitle} backHref={backHref} badge={badge} />
      <Container size="md" py={96}>
        <Paper p={{ base: "xl", md: 40 }} radius={32} shadow="sm" withBorder>
          <Stack align="center" gap="xl" ta="center">
            <ThemeIcon color={isForbidden ? "orange" : "gray"} radius="xl" size={64} variant="light">
              {isForbidden ? <IconLock size={28} /> : <IconSearchOff size={28} />}
            </ThemeIcon>

            <Stack gap="sm" align="center">
              <Title order={1}>
                {isForbidden
                  ? "이 조직에 접근할 권한이 없습니다"
                  : "존재하지 않는 조직입니다"}
              </Title>
              <Text c="dimmed" maw={560}>
                {isForbidden
                  ? "현재 로그인한 계정이 이 조직의 멤버가 아니거나, 이 화면을 볼 수 있는 권한이 없습니다."
                  : "주소가 잘못되었거나 이미 제거된 조직일 수 있습니다. 조직 선택 화면으로 돌아가 다시 확인해 주세요."}
              </Text>
            </Stack>

            {message ? (
              <Alert color={isForbidden ? "orange" : "gray"} icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
                {message}
              </Alert>
            ) : null}

            <Group>
              <Button
                component={Link}
                href="/organizations"
                radius="xl"
                rightSection={<IconArrowRight size={16} />}
              >
                조직 선택으로 이동
              </Button>
              <Button component={Link} href="/login" radius="xl" variant="light">
                로그인 화면
              </Button>
            </Group>
          </Stack>
        </Paper>
      </Container>
    </ConsoleFrame>
  );
}
