"use client";

import { Badge, Group, Paper, Stack, Text, UnstyledButton } from "@mantine/core";
import {
  IconBook2,
  IconChartBar,
  IconKey,
  IconRoute2,
  IconSparkles,
  IconTags,
} from "@tabler/icons-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ComponentType } from "react";

type NavItem = {
  label: string;
  href: string;
  icon: ComponentType<{ size?: number }>;
};

type NavSection = {
  title: string;
  items: NavItem[];
};

const guideSections: NavSection[] = [
  {
    title: "시작하기",
    items: [
      {
        label: "소개",
        href: "/guide",
        icon: IconBook2,
      },
      {
        label: "빠른 시작",
        href: "/quick-start",
        icon: IconSparkles,
      },
      {
        label: "API Key",
        href: "/api-key-guide",
        icon: IconKey,
      },
    ],
  },
  {
    title: "제품 API",
    items: [
      {
        label: "이벤트 전송",
        href: "/send-events",
        icon: IconRoute2,
      },
      {
        label: "데이터 정규화",
        href: "/data-mapping",
        icon: IconTags,
      },
      {
        label: "집계 API",
        href: "/analytics-api",
        icon: IconChartBar,
      },
    ],
  },
] as const;

const guidePaths = new Set(guideSections.flatMap((section) => section.items.map((item) => item.href)));

export function GuideNavigation() {
  const pathname = usePathname();

  if (!guidePaths.has(pathname)) {
    return null;
  }

  return (
    <aside className="dashboard-sidebar">
      <Paper className="console-panel dashboard-sidebar-panel" p="md" radius="24px" shadow="xs" withBorder>
        <Stack gap="md">
          <Stack gap={4}>
            <Text fw={800} size="lg">
              Click Checker
            </Text>
            <Text c="dimmed" size="sm">
              제품 API 가이드
            </Text>
          </Stack>

          {guideSections.map((section, index) => (
            <Stack gap="xs" key={section.title}>
              <Group justify="space-between" align="center">
                <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                  {section.title}
                </Text>
                {index === 0 ? (
                  <Badge color="gray" radius="xl" size="sm" variant="light">
                    6 페이지
                  </Badge>
                ) : null}
              </Group>

              <Stack gap="sm">
                {section.items.map((item) => {
                  const Icon = item.icon;
                  const active = pathname === item.href;

                  return (
                    <UnstyledButton
                      key={item.href}
                      component={Link}
                      href={item.href}
                      className="dashboard-nav-item"
                      style={{
                        width: "100%",
                        borderRadius: 18,
                        padding: "12px 14px",
                        background: active
                          ? "linear-gradient(135deg, rgba(37,99,235,0.14), rgba(59,130,246,0.2))"
                          : "rgba(255,255,255,0.64)",
                        border: active
                          ? "1px solid rgba(59,130,246,0.28)"
                          : "1px solid rgba(148,163,184,0.18)",
                        boxShadow: active ? "0 8px 24px rgba(59,130,246,0.12)" : "none",
                      }}
                    >
                      <Group gap="xs" justify="space-between" wrap="nowrap">
                        <Group gap="xs" wrap="nowrap">
                          <Icon size={16} />
                          <Text fw={active ? 700 : 600} size="sm">
                            {item.label}
                          </Text>
                        </Group>
                        {active ? (
                          <Badge color="blue" radius="xl" size="xs" variant="light">
                            현재
                          </Badge>
                        ) : null}
                      </Group>
                    </UnstyledButton>
                  );
                })}
              </Stack>
            </Stack>
          ))}
        </Stack>
      </Paper>
    </aside>
  );
}
