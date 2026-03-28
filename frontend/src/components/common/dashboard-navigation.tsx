"use client";

import {
  Badge,
  Group,
  Paper,
  Stack,
  Text,
  UnstyledButton,
} from "@mantine/core";
import {
  IconActivity,
  IconAdjustments,
  IconChartBar,
  IconChartLine,
  IconFilterBolt,
  IconGitBranch,
  IconKey,
  IconRoute2,
  IconTags,
  IconUsers,
  IconUsersGroup,
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

function isActivePath(pathname: string, href: string) {
  if (href === pathname) {
    return true;
  }

  const hrefSegments = href.split("/").filter(Boolean);
  if (hrefSegments.length === 2) {
    return false;
  }

  return href !== "/dashboard" && pathname.startsWith(`${href}/`);
}

export function DashboardNavigation() {
  const pathname = usePathname();
  const segments = pathname.split("/").filter(Boolean);

  if (segments[0] !== "dashboard" || !segments[1]) {
    return null;
  }

  const organizationId = segments[1];

  const sections: NavSection[] = [
    {
      title: "분석",
      items: [
        {
          label: "개요",
          href: `/dashboard/${organizationId}`,
          icon: IconChartBar,
        },
        {
          label: "추이",
          href: `/dashboard/${organizationId}/trends`,
          icon: IconChartLine,
        },
        {
          label: "사용자 현황",
          href: `/dashboard/${organizationId}/users`,
          icon: IconUsersGroup,
        },
        {
          label: "활동량",
          href: `/dashboard/${organizationId}/activity`,
          icon: IconActivity,
        },
        {
          label: "유지율",
          href: `/dashboard/${organizationId}/retention`,
          icon: IconGitBranch,
        },
        {
          label: "퍼널 분석",
          href: `/dashboard/${organizationId}/funnels`,
          icon: IconFilterBolt,
        },
      ],
    },
    {
      title: "설정",
      items: [
        {
          label: "경로 규칙",
          href: `/dashboard/${organizationId}/route-templates`,
          icon: IconRoute2,
        },
        {
          label: "이벤트 규칙",
          href: `/dashboard/${organizationId}/event-type-mappings`,
          icon: IconTags,
        },
        {
          label: "API Key 관리",
          href: `/dashboard/${organizationId}/api-key`,
          icon: IconKey,
        },
        {
          label: "멤버 관리",
          href: `/dashboard/${organizationId}/members`,
          icon: IconUsers,
        },
      ],
    },
  ];

  return (
    <aside className="dashboard-sidebar">
      <Paper className="console-panel dashboard-sidebar-panel" p="md" radius="24px" shadow="xs" withBorder>
        <Stack gap="md">
          <Stack gap={4}>
            <Text fw={800} size="lg">
              Click Checker
            </Text>
            <Text c="dimmed" size="sm">
              관리자 콘솔
            </Text>
          </Stack>

          {sections.map((section) => (
            <Stack key={section.title} gap="xs">
              <Group justify="space-between" align="center">
                <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                  {section.title}
                </Text>
                {section.title === "설정" ? (
                  <Badge color="gray" radius="xl" size="sm" variant="light">
                    조직 {organizationId}
                  </Badge>
                ) : null}
              </Group>

              <Stack gap="sm">
                {section.items.map((item) => {
                  const Icon = item.icon;
                  const active = isActivePath(pathname, item.href);

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
