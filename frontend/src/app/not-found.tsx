"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { Badge, Button, Container, Group, Paper, Stack, Text, ThemeIcon, Title } from "@mantine/core";
import { IconArrowRight, IconCompassOff } from "@tabler/icons-react";
import Link from "next/link";

export default function NotFound() {
  return (
    <ConsoleFrame>
      <Container size="md" py={96}>
        <Paper
          p={{ base: "xl", md: 40 }}
          radius={32}
          shadow="sm"
          withBorder
          style={{
            background:
              "radial-gradient(circle at top right, rgba(59,130,246,0.12), transparent 30%), linear-gradient(180deg, rgba(255,255,255,0.98), rgba(248,250,252,0.98))",
          }}
        >
          <Stack align="center" gap="xl" ta="center">
            <ThemeIcon color="blue" radius="xl" size={64} variant="light">
              <IconCompassOff size={28} />
            </ThemeIcon>

            <Stack gap="sm" align="center">
              <Badge color="blue" radius="xl" variant="light">
                404 Not Found
              </Badge>
              <Title order={1}>페이지를 찾을 수 없습니다</Title>
              <Text c="dimmed" maw={560}>
                주소가 잘못되었거나, 현재 로그인한 계정으로 접근할 수 없는 화면일 수 있습니다.
                organization 선택 화면으로 돌아가서 다시 진입하는 편이 가장 안전합니다.
              </Text>
            </Stack>

            <Group>
              <Button
                component={Link}
                href="/organizations"
                radius="xl"
                rightSection={<IconArrowRight size={16} />}
                size="md"
              >
                Organization 선택으로 이동
              </Button>
              <Button component={Link} href="/login" radius="xl" size="md" variant="light" color="gray">
                로그인 화면
              </Button>
            </Group>
          </Stack>
        </Paper>
      </Container>
    </ConsoleFrame>
  );
}
