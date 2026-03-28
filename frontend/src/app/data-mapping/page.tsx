"use client";

import { GuideFrame } from "@/components/guide/guide-frame";
import {
  Badge,
  Code,
  Container,
  Paper,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Title,
} from "@mantine/core";

const routeExamples = [
  {
    raw: "/products/{id}",
    example: "/products/10, /products/24",
    mapped: "product_detail",
  },
  {
    raw: "/products/{id}/reviews",
    example: "/products/10/reviews, /products/24/reviews",
    mapped: "product_reviews",
  },
  {
    raw: "/account/{section}",
    example: "/account/profile, /account/orders",
    mapped: "account_section",
  },
  {
    raw: "/orders/{id}",
    example: "/orders/1201, /orders/9831",
    mapped: "order_detail",
  },
  {
    raw: "/promo/spring-2026",
    example: "/promo/spring-2026",
    mapped: "UNMATCHED_ROUTE",
  },
] as const;

const eventTypeExamples = [
  {
    raw: "page_view",
    mapped: "PAGE_VIEW",
  },
  {
    raw: "product_view",
    mapped: "PAGE_VIEW",
  },
  {
    raw: "signup_submit",
    mapped: "SIGN_UP",
  },
  {
    raw: "register_submit",
    mapped: "SIGN_UP",
  },
  {
    raw: "purchase_complete",
    mapped: "PURCHASE",
  },
  {
    raw: "mystery_event",
    mapped: "UNMAPPED_EVENT_TYPE",
  },
] as const;

const mappingApis = [
  {
    path: "GET /api/events/route-templates",
    description: "현재 조직의 경로 규칙 목록을 조회합니다.",
  },
  {
    path: "POST /api/events/route-templates",
    description: "새 경로 규칙을 추가합니다.",
  },
  {
    path: "GET /api/events/event-type-mappings",
    description: "현재 조직의 이벤트 매핑 목록을 조회합니다.",
  },
  {
    path: "POST /api/events/event-type-mappings",
    description: "새 이벤트 매핑을 추가합니다.",
  },
] as const;

const mappingHints = [
  {
    title: "경로는 route key로 보입니다",
    description: "개요, 경로 상세, 퍼널 조건에서 route key 기준으로 읽게 됩니다.",
  },
  {
    title: "이벤트는 공통 키로 보입니다",
    description: "개요, 이벤트 상세, 퍼널 단계에서 공통 이벤트 기준으로 읽게 됩니다.",
  },
  {
    title: "정리되지 않으면 별도 값으로 남습니다",
    description: "`UNMATCHED_ROUTE`, `UNMAPPED_EVENT_TYPE`는 아직 규칙에 묶이지 않은 값입니다.",
  },
] as const;

const codeFontFamily =
  "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, Courier New, monospace";

type ExampleChipProps = {
  children: string;
  tone: "orange" | "teal";
};

function ExampleChip({ children, tone }: ExampleChipProps) {
  const palette =
    tone === "orange"
      ? {
          background: "rgba(255, 237, 213, 0.9)",
          border: "rgba(251, 146, 60, 0.28)",
          color: "#9a3412",
        }
      : {
          background: "rgba(204, 251, 241, 0.92)",
          border: "rgba(20, 184, 166, 0.28)",
          color: "#115e59",
        };

  return (
    <Text
      component="span"
      fw={700}
      px="sm"
      py={6}
      size="sm"
      style={{
        display: "inline-flex",
        alignItems: "center",
        width: "fit-content",
        borderRadius: 14,
        background: palette.background,
        border: `1px solid ${palette.border}`,
        color: palette.color,
        lineHeight: 1.2,
        fontFamily: codeFontFamily,
      }}
    >
      {children}
    </Text>
  );
}

export default function DataMappingPage() {
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
                데이터 정규화
              </Badge>
              <Title order={1} style={{ fontSize: "clamp(2rem, 5vw, 3.2rem)", lineHeight: 1.08 }}>
                원본 경로와 이벤트 이름을
                <br />
                분석용 키로 정리합니다.
              </Title>
              <Text c="dimmed" maw={760} size="lg">
                제품 API는 raw path와 raw event type을 그대로 받아 저장할 수 있지만, 분석 화면에서는 route key와 canonical event type으로 정리된 값이 더 읽기 쉽습니다.
              </Text>
            </Stack>
          </Paper>

          <SimpleGrid cols={{ base: 1, md: 3 }} spacing="lg">
            {mappingHints.map((item) => (
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
                <Stack gap={4}>
                  <Badge color="teal" radius="xl" variant="light" w="fit-content">
                    Route Key
                  </Badge>
                  <Title order={3}>경로는 route key로 묶습니다</Title>
                  <Text c="dimmed" size="sm">
                    경로 규칙은 `/products/{`id`}`처럼 템플릿 형태로 둘 수 있습니다. 즉 `{`id`}` 같은 자리표시자를 써서 여러 raw path를 하나의 route key로 정리할 수 있습니다.
                  </Text>
                </Stack>

                <Table.ScrollContainer minWidth={520}>
                  <Table highlightOnHover>
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th>경로 템플릿</Table.Th>
                        <Table.Th>예시 path</Table.Th>
                        <Table.Th>분석 값</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {routeExamples.map((example) => (
                        <Table.Tr key={example.raw}>
                          <Table.Td>
                            <Text fw={700} size="sm" style={{ fontFamily: codeFontFamily }}>
                              {example.raw}
                            </Text>
                          </Table.Td>
                          <Table.Td>
                            <Text size="sm">{example.example}</Text>
                          </Table.Td>
                          <Table.Td>{example.mapped}</Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Table.ScrollContainer>
              </Stack>
            </Paper>

            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Stack gap={4}>
                  <Badge color="orange" radius="xl" variant="light" w="fit-content">
                    이벤트 공통 키
                  </Badge>
                  <Title order={3}>이벤트 이름도 공통 키로 정리합니다</Title>
                </Stack>

                <Table.ScrollContainer minWidth={520}>
                  <Table highlightOnHover>
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th>원본 event type</Table.Th>
                        <Table.Th>분석 값</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {eventTypeExamples.map((example) => (
                        <Table.Tr key={example.raw}>
                          <Table.Td>
                            <Text fw={700} size="sm" style={{ fontFamily: codeFontFamily }}>
                              {example.raw}
                            </Text>
                          </Table.Td>
                          <Table.Td>{example.mapped}</Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Table.ScrollContainer>
              </Stack>
            </Paper>
          </SimpleGrid>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="lg">
              <Stack gap={4}>
                <Badge color="blue" radius="xl" variant="light" w="fit-content">
                  왜 필요한가
                </Badge>
                <Title order={2}>같은 의미의 raw 값을 한 키로 묶기 위해 사용합니다</Title>
                <Text c="dimmed" size="sm">
                  정규화가 없으면 비슷한 값이 여러 줄로 흩어지고, 정규화가 있으면 하나의 분석 값으로 모여서 더 읽기 쉬운 결과를 볼 수 있습니다.
                </Text>
              </Stack>

              <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
                <Paper bg="gray.0" p="lg" radius="24px" withBorder>
                  <Stack gap="md">
                    <Stack gap={4}>
                      <Badge color="teal" radius="xl" variant="light" w="fit-content">
                        Route Key
                      </Badge>
                      <Text fw={700} size="lg">
                        상세 경로를 공통 경로로 묶습니다
                      </Text>
                      <Text c="dimmed" size="sm">
                        URL마다 id가 달라지면 상위 경로 집계가 흩어집니다. route key로 정리하면 같은 화면을 한 값으로 읽을 수 있습니다.
                      </Text>
                    </Stack>

                    <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
                      <Paper bg="orange.0" p="md" radius="20px" withBorder>
                        <Stack gap="xs">
                          <Badge color="orange" radius="xl" variant="light" w="fit-content">
                            정리 전
                          </Badge>
                          <Stack gap={8}>
                            <ExampleChip tone="orange">/products/10</ExampleChip>
                            <ExampleChip tone="orange">/products/24</ExampleChip>
                            <ExampleChip tone="orange">/products/91</ExampleChip>
                          </Stack>
                        </Stack>
                      </Paper>

                      <Paper bg="teal.0" p="md" radius="20px" withBorder>
                        <Stack gap="xs">
                          <Badge color="teal" radius="xl" variant="light" w="fit-content">
                            정리 후
                          </Badge>
                          <Stack gap={8}>
                            <ExampleChip tone="teal">{"/products/{id}"}</ExampleChip>
                            <ExampleChip tone="teal">product_detail</ExampleChip>
                          </Stack>
                        </Stack>
                      </Paper>
                    </SimpleGrid>
                  </Stack>
                </Paper>

                <Paper bg="gray.0" p="lg" radius="24px" withBorder>
                  <Stack gap="md">
                    <Stack gap={4}>
                      <Badge color="orange" radius="xl" variant="light" w="fit-content">
                        Event Type
                      </Badge>
                      <Text fw={700} size="lg">
                        비슷한 이벤트 이름을 공통 이벤트로 묶습니다
                      </Text>
                      <Text c="dimmed" size="sm">
                        raw event type이 제각각이면 overview와 funnels에서 같은 행동이 여러 줄로 나뉩니다. 공통 키로 정리하면 행동 단위로 해석하기 쉬워집니다.
                      </Text>
                    </Stack>

                    <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
                      <Paper bg="orange.0" p="md" radius="20px" withBorder>
                        <Stack gap="xs">
                          <Badge color="orange" radius="xl" variant="light" w="fit-content">
                            정리 전
                          </Badge>
                          <Stack gap={8}>
                            <ExampleChip tone="orange">page_view</ExampleChip>
                            <ExampleChip tone="orange">product_view</ExampleChip>
                            <ExampleChip tone="orange">register_submit</ExampleChip>
                          </Stack>
                        </Stack>
                      </Paper>

                      <Paper bg="teal.0" p="md" radius="20px" withBorder>
                        <Stack gap="xs">
                          <Badge color="teal" radius="xl" variant="light" w="fit-content">
                            정리 후
                          </Badge>
                          <Stack gap={8}>
                            <ExampleChip tone="teal">PAGE_VIEW</ExampleChip>
                            <ExampleChip tone="teal">SIGN_UP</ExampleChip>
                          </Stack>
                        </Stack>
                      </Paper>
                    </SimpleGrid>
                  </Stack>
                </Paper>
              </SimpleGrid>
            </Stack>
          </Paper>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="lg">
              <Stack gap={4}>
                <Badge color="gray" radius="xl" variant="light" w="fit-content">
                  관리 방법
                </Badge>
                <Title order={2}>콘솔과 제품 API 둘 다 사용할 수 있습니다</Title>
              </Stack>

              <Table.ScrollContainer minWidth={720}>
                <Table highlightOnHover>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>경로</Table.Th>
                      <Table.Th>설명</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {mappingApis.map((api) => (
                      <Table.Tr key={api.path}>
                        <Table.Td>
                          <Code>{api.path}</Code>
                        </Table.Td>
                        <Table.Td>{api.description}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Table.ScrollContainer>

              <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
                <Paper bg="gray.0" p="md" radius="20px" withBorder>
                  <Stack gap={6}>
                    <Text fw={700} size="sm">
                      콘솔에서 관리
                    </Text>
                    <Text size="sm" style={{ fontFamily: codeFontFamily }}>
                      /dashboard/{`{organizationId}`}/route-templates
                    </Text>
                    <Text size="sm" style={{ fontFamily: codeFontFamily }}>
                      /dashboard/{`{organizationId}`}/event-type-mappings
                    </Text>
                  </Stack>
                </Paper>
                <Paper bg="gray.0" p="md" radius="20px" withBorder>
                  <Stack gap={6}>
                    <Text fw={700} size="sm">
                      미매핑 값
                    </Text>
                    <Text c="dimmed" size="sm">
                      규칙에 걸리지 않은 path나 event type은 unmatched/unmapped 형태로 집계에 남을 수 있습니다.
                    </Text>
                  </Stack>
                </Paper>
              </SimpleGrid>
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </GuideFrame>
  );
}
