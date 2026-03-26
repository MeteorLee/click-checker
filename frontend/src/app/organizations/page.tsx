"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import { fetchMe } from "@/lib/api/auth";
import { clearAccessToken, getAccessToken } from "@/lib/session/token-store";
import type { AdminMeMembership } from "@/types/auth";
import {
  Alert,
  Badge,
  Button,
  Card,
  Container,
  Group,
  Loader,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  Title,
  ThemeIcon,
} from "@mantine/core";
import { IconAlertCircle, IconArrowRight, IconBuildingSkyscraper } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

type OrganizationsState = {
  loginId: string;
  memberships: AdminMeMembership[];
};

export default function OrganizationsPage() {
  const router = useRouter();
  const [data, setData] = useState<OrganizationsState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function load() {
      const accessToken = getAccessToken();

      if (!accessToken) {
        router.replace("/login");
        return;
      }

      try {
        const me = await fetchMe(accessToken);
        setData({
          loginId: me.loginId,
          memberships: me.memberships,
        });
      } catch (error) {
        const message =
          error instanceof Error
            ? error.message
            : "organization 목록을 불러오지 못했습니다.";

        if ("status" in (error as object) && (error as { status?: number }).status === 401) {
          clearAccessToken();
          router.replace("/login");
          return;
        }

        setErrorMessage(message);
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [router]);

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Organization 선택"
          subtitle="로그인한 계정이 접근 가능한 organization 목록을 불러오는 중입니다."
        />
        <Container size="md" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">organization 목록을 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Organization 선택"
          subtitle="로그인한 계정이 접근 가능한 organization 목록을 확인합니다."
        />
        <Container size="sm" py={96}>
          <Alert
            color="red"
            icon={<IconAlertCircle size={18} />}
            radius="lg"
            variant="light"
          >
            {errorMessage}
          </Alert>
        </Container>
      </ConsoleFrame>
    );
  }

  if (!data) {
    return null;
  }

  return (
    <ConsoleFrame>
      <ConsoleHeader
        title="Organization 선택"
        subtitle={`${data.loginId} 계정이 접근 가능한 organization 목록입니다. organization을 하나 고르면 overview 대시보드로 이동합니다.`}
      />
      <Container size="lg" pb={72}>
        <Stack gap="xl">
          <Group justify="space-between" align="center">
            <Stack gap={4}>
              <Text fw={700} size="xl">
                접근 가능한 organization
              </Text>
              <Text c="dimmed" size="sm">
                현재 계정의 membership을 기준으로 접근 가능한 조직만 표시합니다.
              </Text>
            </Stack>
            <Badge color="dark" radius="xl" size="lg" variant="light">
              {data.memberships.length} organizations
            </Badge>
          </Group>

          {data.memberships.length === 0 ? (
            <Alert radius="lg" variant="light" color="gray">
              접근 가능한 organization이 없습니다.
            </Alert>
          ) : (
            <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
              {data.memberships.map((membership) => (
                <Card
                  key={membership.membershipId}
                  radius="28px"
                  p="xl"
                  shadow="sm"
                  withBorder
                  className="console-panel"
                >
                  <Stack gap="lg">
                    <Group justify="space-between" align="flex-start">
                      <Group align="flex-start" gap="md" wrap="nowrap">
                        <ThemeIcon color="blue" radius="xl" size={48} variant="light">
                          <IconBuildingSkyscraper size={22} />
                        </ThemeIcon>
                        <div>
                          <Text fw={700} size="lg">
                            {membership.organizationName}
                          </Text>
                          <Text c="dimmed" size="sm">
                            organizationId: {membership.organizationId}
                          </Text>
                        </div>
                      </Group>
                      <Badge variant="light" color="blue">
                        {membership.role}
                      </Badge>
                    </Group>

                    <Paper radius="lg" p="md" bg="gray.0">
                      <Text c="dimmed" size="sm">
                        이 organization의 overview, top routes, top event types를 조회할 수
                        있습니다.
                      </Text>
                    </Paper>

                    <Group justify="flex-end">
                      <Button
                        radius="xl"
                        rightSection={<IconArrowRight size={16} />}
                        onClick={() => router.push(`/dashboard/${membership.organizationId}`)}
                      >
                        대시보드 열기
                      </Button>
                    </Group>
                  </Stack>
                </Card>
              ))}
            </SimpleGrid>
          )}
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}
