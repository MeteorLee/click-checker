"use client";

import { GuideFrame } from "@/components/guide/guide-frame";
import { buildApiUrl } from "@/lib/api/config";
import {
  Badge,
  Code,
  Container,
  Group,
  Paper,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Title,
} from "@mantine/core";
import { IconRoute2 } from "@tabler/icons-react";

const curlExample = `curl -X POST ${buildApiUrl("/api/events")} \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: ck_live_xxx" \\
  -d '{
    "eventType": "page_view",
    "path": "/pricing",
    "externalUserId": "user_123",
    "occurredAt": "2026-03-29T12:00:00Z"
  }'`;

const fetchExample = `await fetch("${buildApiUrl("/api/events")}", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "X-API-Key": "ck_live_xxx",
  },
  body: JSON.stringify({
    eventType: "page_view",
    path: "/pricing",
    externalUserId: "user_123",
    occurredAt: new Date().toISOString(),
  }),
});`;

const responseExample = `{
  "id": 6628534
}`;

const fields = [
  {
    name: "eventType",
    required: "필수",
    description: "이벤트 이름입니다. 예: page_view, click, purchase",
  },
  {
    name: "path",
    required: "필수",
    description: "이벤트가 발생한 경로입니다. 예: /pricing, /products/10",
  },
  {
    name: "occurredAt",
    required: "필수",
    description: "이벤트가 발생한 시각입니다. ISO-8601 UTC 형식을 사용합니다.",
  },
  {
    name: "externalUserId",
    required: "선택",
    description: "식별 가능한 사용자 ID입니다. 없으면 익명 이벤트로 저장됩니다.",
  },
  {
    name: "payload",
    required: "선택",
    description: "추가 데이터를 문자열로 보낼 때 사용합니다.",
  },
] as const;

const cautions = [
  {
    title: "occurredAt은 UTC ISO-8601 형식",
    description: "분석 API의 from/to와 맞추려면 occurredAt도 UTC 기준 문자열로 보내는 편이 가장 안전합니다.",
  },
  {
    title: "externalUserId가 없으면 익명 이벤트",
    description: "값을 넣지 않으면 이벤트는 저장되지만 사용자 현황/유지율 계산에서는 익명 이벤트로만 잡힙니다.",
  },
  {
    title: "payload는 선택 필드",
    description: "추가 문자열 정보를 남기고 싶을 때만 사용합니다. 없어도 이벤트 적재에는 문제 없습니다.",
  },
] as const;

const sendEventChecklist = [
  {
    title: "필수는 2개",
    description: "`eventType`, `path`만 있어도 기본 적재는 시작할 수 있습니다.",
  },
  {
    title: "식별은 externalUserId",
    description: "사용자 현황과 유지율까지 보려면 externalUserId를 함께 보내는 편이 좋습니다.",
  },
  {
    title: "성공 확인은 id",
    description: "응답에 id가 오면 저장은 끝났고, 그 다음은 개요 값이나 집계 API로 확인합니다.",
  },
] as const;

export default function SendEventsPage() {
  return (
    <GuideFrame>
      <Container size="xl" py={56}>
        <Stack gap="xl">
          <Paper
            p={{ base: "xl", md: 40 }}
            radius={36}
            shadow="sm"
            withBorder
            style={{
              background:
                "radial-gradient(circle at top right, rgba(59,130,246,0.12), transparent 28%), radial-gradient(circle at top left, rgba(20,184,166,0.10), transparent 24%), linear-gradient(180deg, rgba(255,255,255,0.98), rgba(248,250,252,0.98))",
            }}
          >
            <Stack gap="lg">
              <Badge color="blue" radius="xl" variant="light" w="fit-content">
                이벤트 전송
              </Badge>
              <Title order={1} style={{ fontSize: "clamp(2rem, 5vw, 3.2rem)", lineHeight: 1.08 }}>
                이벤트는 `POST /api/events`
                <br />
                하나로 보냅니다.
              </Title>
              <Text c="dimmed" maw={760} size="lg">
                제품 API는 `X-API-Key`와 JSON body만 있으면 됩니다. 아래 예시를 그대로 복붙한 뒤 값만 바꾸면 바로 적재할 수 있습니다.
              </Text>
            </Stack>
          </Paper>

          <SimpleGrid cols={{ base: 1, md: 3 }} spacing="lg">
            {sendEventChecklist.map((item) => (
              <Paper key={item.title} radius="24px" p="lg" withBorder bg="gray.0">
                <Stack gap={6}>
                  <Text fw={700} size="sm">
                    {item.title}
                  </Text>
                  <Text c="dimmed" size="sm">
                    {item.description}
                  </Text>
                </Stack>
              </Paper>
            ))}
          </SimpleGrid>

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Group gap="sm">
                  <Badge color="teal" radius="xl" variant="light">
                    헤더
                  </Badge>
                  <Badge color="gray" radius="xl" variant="light">
                    POST /api/events
                  </Badge>
                </Group>
                <Code block>{`Content-Type: application/json\nX-API-Key: ck_live_xxx`}</Code>
                <Text c="dimmed" size="sm">
                  `X-API-Key`는 조직의 수집용 API key입니다. 관리자 콘솔의 `API Key 관리`에서 확인할 수 있습니다.
                </Text>
              </Stack>
            </Paper>

            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Group gap="sm">
                  <Badge color="orange" radius="xl" variant="light">
                    성공 응답
                  </Badge>
                  <Badge color="gray" radius="xl" variant="light">
                    반환 값
                  </Badge>
                </Group>
                <Code block>{responseExample}</Code>
                <Text c="dimmed" size="sm">
                  응답에 `id`가 오면 이벤트 저장은 완료입니다. 이후 개요 화면이나 분석 API에서 반영 여부를 확인할 수 있습니다.
                </Text>
              </Stack>
            </Paper>
          </SimpleGrid>

          <SimpleGrid cols={{ base: 1, md: 3 }} spacing="lg">
            {cautions.map((item) => (
              <Paper key={item.title} radius="24px" p="lg" withBorder bg="gray.0">
                <Stack gap={6}>
                  <Text fw={700} size="sm">
                    {item.title}
                  </Text>
                  <Text c="dimmed" size="sm">
                    {item.description}
                  </Text>
                </Stack>
              </Paper>
            ))}
          </SimpleGrid>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="lg">
              <Stack gap={4}>
                <Badge color="blue" radius="xl" variant="light" w="fit-content">
                  요청 본문
                </Badge>
                <Title order={2}>필드 설명</Title>
              </Stack>

              <Table.ScrollContainer minWidth={720}>
                <Table highlightOnHover>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>필드</Table.Th>
                      <Table.Th>필수 여부</Table.Th>
                      <Table.Th>설명</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {fields.map((field) => (
                      <Table.Tr key={field.name}>
                        <Table.Td>
                          <Code>{field.name}</Code>
                        </Table.Td>
                        <Table.Td>{field.required}</Table.Td>
                        <Table.Td>{field.description}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Table.ScrollContainer>
            </Stack>
          </Paper>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="lg">
              <Stack gap={4}>
                <Badge color="gray" radius="xl" variant="light" w="fit-content">
                  성공 확인
                </Badge>
                <Title order={2}>이 요청이 잘 들어갔는지 어떻게 확인하나</Title>
                <Text c="dimmed" size="sm">
                  응답의 `id`로 저장 성공을 먼저 확인하고, 그 다음에는 개요 값이나 집계 API에서 total events가 늘었는지 보면 됩니다.
                </Text>
              </Stack>
            </Stack>
          </Paper>

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Group gap="sm">
                  <Badge color="blue" radius="xl" variant="light">
                    cURL
                  </Badge>
                  <IconRoute2 size={18} />
                </Group>
                <Code block>{curlExample}</Code>
              </Stack>
            </Paper>

            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Group gap="sm">
                  <Badge color="teal" radius="xl" variant="light">
                    JavaScript
                  </Badge>
                  <IconRoute2 size={18} />
                </Group>
                <Code block>{fetchExample}</Code>
              </Stack>
            </Paper>
          </SimpleGrid>
        </Stack>
      </Container>
    </GuideFrame>
  );
}
