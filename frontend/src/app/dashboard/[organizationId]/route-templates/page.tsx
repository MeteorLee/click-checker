"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import { fetchMe } from "@/lib/api/auth";
import {
  createRouteTemplate,
  deleteRouteTemplate,
  fetchRouteTemplates,
  updateRouteTemplate,
  updateRouteTemplateActive,
} from "@/lib/api/rules";
import { getAccessToken } from "@/lib/session/token-store";
import type {
  RouteTemplateCreateRequest,
  RouteTemplateItem,
  RouteTemplateUpdateRequest,
} from "@/types/rules";
import {
  Alert,
  Badge,
  Button,
  Container,
  Group,
  Loader,
  Modal,
  Paper,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { IconAlertCircle, IconEdit, IconPlus, IconTrash } from "@tabler/icons-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

type RouteTemplatePageState = {
  organizationName: string;
  role: string | null;
  items: RouteTemplateItem[];
};

type RouteTemplateFormState = {
  template: string;
  routeKey: string;
  priority: string;
};

const EMPTY_FORM: RouteTemplateFormState = {
  template: "",
  routeKey: "",
  priority: "100",
};

export default function RouteTemplatesPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<RouteTemplatePageState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitErrorMessage, setSubmitErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isEditOpened, setIsEditOpened] = useState(false);
  const [editingItem, setEditingItem] = useState<RouteTemplateItem | null>(null);
  const [createForm, setCreateForm] = useState<RouteTemplateFormState>(EMPTY_FORM);
  const [editForm, setEditForm] = useState<RouteTemplateFormState>(EMPTY_FORM);

  useEffect(() => {
    async function load() {
      const accessToken = getAccessToken();
      if (!accessToken) {
        router.replace("/login");
        return;
      }

      setIsLoading(true);
      setErrorMessage(null);

      try {
        const [me, response] = await Promise.all([
          fetchMe(accessToken),
          fetchRouteTemplates(accessToken, params.organizationId),
        ]);

        const currentMembership = me.memberships.find(
          (membership) => String(membership.organizationId) === params.organizationId,
        );

        setData({
          organizationName: currentMembership?.organizationName ?? `Organization ${params.organizationId}`,
          role: currentMembership?.role ?? null,
          items: response.items,
        });
      } catch (error) {
        const status = "status" in (error as object) ? (error as { status?: number }).status : undefined;
        if (status === 401) {
          router.replace("/login");
          return;
        }
        setErrorMessage(error instanceof Error ? error.message : "route template 목록을 불러오지 못했습니다.");
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [params.organizationId, router]);

  const canManage = data?.role === "ADMIN" || data?.role === "OWNER";

  function parseForm(form: RouteTemplateFormState): RouteTemplateCreateRequest | RouteTemplateUpdateRequest {
    return {
      template: form.template.trim(),
      routeKey: form.routeKey.trim(),
      priority: Number(form.priority),
    };
  }

  function openEdit(item: RouteTemplateItem) {
    setEditingItem(item);
    setEditForm({
      template: item.template,
      routeKey: item.routeKey,
      priority: String(item.priority),
    });
    setSubmitErrorMessage(null);
    setIsEditOpened(true);
  }

  async function handleCreate() {
    const accessToken = getAccessToken();
    if (!accessToken || !data) return;

    setIsSubmitting(true);
    setSubmitErrorMessage(null);
    try {
      const created = await createRouteTemplate(accessToken, params.organizationId, parseForm(createForm));
      setData({
        ...data,
        items: [created, ...data.items].sort((a, b) => b.priority - a.priority || a.id - b.id),
      });
      setCreateForm(EMPTY_FORM);
    } catch (error) {
      setSubmitErrorMessage(error instanceof Error ? error.message : "route template를 추가하지 못했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleUpdate() {
    const accessToken = getAccessToken();
    if (!accessToken || !data || !editingItem) return;

    setIsSubmitting(true);
    setSubmitErrorMessage(null);
    try {
      const updated = await updateRouteTemplate(
        accessToken,
        params.organizationId,
        editingItem.id,
        parseForm(editForm),
      );
      setData({
        ...data,
        items: data.items
          .map((item) => (item.id === updated.id ? updated : item))
          .sort((a, b) => b.priority - a.priority || a.id - b.id),
      });
      setIsEditOpened(false);
      setEditingItem(null);
    } catch (error) {
      setSubmitErrorMessage(error instanceof Error ? error.message : "route template를 수정하지 못했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleToggleActive(item: RouteTemplateItem, active: boolean) {
    const accessToken = getAccessToken();
    if (!accessToken || !data) return;

    try {
      const updated = await updateRouteTemplateActive(accessToken, params.organizationId, item.id, active);
      setData({
        ...data,
        items: data.items.map((current) => (current.id === updated.id ? updated : current)),
      });
    } catch (error) {
      setSubmitErrorMessage(error instanceof Error ? error.message : "route template 상태를 바꾸지 못했습니다.");
    }
  }

  async function handleDelete(item: RouteTemplateItem) {
    const accessToken = getAccessToken();
    if (!accessToken || !data) return;

    try {
      await deleteRouteTemplate(accessToken, params.organizationId, item.id);
      setData({
        ...data,
        items: data.items.filter((current) => current.id !== item.id),
      });
    } catch (error) {
      setSubmitErrorMessage(error instanceof Error ? error.message : "route template를 삭제하지 못했습니다.");
    }
  }

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Route Template 관리"
          subtitle="route 규칙 목록을 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}/routes`}
          badge="Settings"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">route template 목록을 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage || !data) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Route Template 관리"
          subtitle="route 규칙 테이블을 확인합니다."
          backHref={`/dashboard/${params.organizationId}/routes`}
          badge="Settings"
        />
        <Container size="md" py={96}>
          <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
            {errorMessage ?? "route template 목록을 불러오지 못했습니다."}
          </Alert>
        </Container>
      </ConsoleFrame>
    );
  }

  return (
    <ConsoleFrame>
      <ConsoleHeader
        title={`${data.organizationName} Route Template 관리`}
        subtitle="raw path를 route key로 정규화하는 규칙을 조회하고 수정합니다."
        backHref={`/dashboard/${params.organizationId}/routes`}
        badge="Settings"
      />
      <Modal
        centered
        opened={isEditOpened}
        radius="28px"
        title="Route Template 수정"
        onClose={() => setIsEditOpened(false)}
      >
        <Stack gap="md">
          <TextInput
            description="실제 path 패턴입니다. path variable은 {id}처럼 적습니다."
            label="Template"
            radius="xl"
            value={editForm.template}
            onChange={(event) => setEditForm((current) => ({ ...current, template: event.currentTarget.value }))}
          />
          <TextInput
            description="집계에서 사용할 대표 키입니다. 언더바 형식으로 짧게 정리하면 읽기 쉽습니다."
            label="Route Key"
            radius="xl"
            value={editForm.routeKey}
            onChange={(event) => setEditForm((current) => ({ ...current, routeKey: event.currentTarget.value }))}
          />
          <TextInput
            description="값이 클수록 먼저 매칭합니다. 더 구체적인 규칙에 높은 우선순위를 두는 편이 안전합니다."
            label="Priority"
            radius="xl"
            type="number"
            value={editForm.priority}
            onChange={(event) => setEditForm((current) => ({ ...current, priority: event.currentTarget.value }))}
          />
          <Group justify="flex-end">
            <Button radius="xl" variant="default" onClick={() => setIsEditOpened(false)}>
              취소
            </Button>
            <Button loading={isSubmitting} radius="xl" onClick={handleUpdate}>
              수정 저장
            </Button>
          </Group>
        </Stack>
      </Modal>
      <Container size="xl" pb={72}>
        <Stack gap="xl">
          <Paper radius="32px" p={40} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <div>
                  <Badge color="blue" mb="md" variant="light">
                    Route Rules
                  </Badge>
                  <Title order={1}>Route Template 테이블</Title>
                  <Text c="dimmed" mt="sm">
                    path를 어떤 route key로 묶을지와 우선순위를 관리합니다.
                  </Text>
                </div>
                <Badge color="dark" radius="xl" variant="light">
                  {data.items.length} rules
                </Badge>
              </Group>

              {submitErrorMessage ? (
                <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
                  {submitErrorMessage}
                </Alert>
              ) : null}

              <Paper p="lg" radius="24px" withBorder>
                <Stack gap="md">
                  <Group justify="space-between" align="center">
                    <Text fw={700}>새 route template 추가</Text>
                    <Badge color={canManage ? "blue" : "gray"} variant="light">
                      {canManage ? "ADMIN 이상 수정 가능" : "VIEWER는 조회만 가능"}
                    </Badge>
                  </Group>
                  <Group align="flex-end" grow>
                    <TextInput
                      disabled={!canManage}
                      description="실제 path 패턴입니다. path variable은 {id}처럼 적습니다."
                      label="Template"
                      placeholder="/posts/{id}/comments"
                      radius="xl"
                      value={createForm.template}
                      onChange={(event) => setCreateForm((current) => ({ ...current, template: event.currentTarget.value }))}
                    />
                    <TextInput
                      disabled={!canManage}
                      description="집계에서 사용할 대표 키입니다. 언더바 형식으로 짧게 정리하면 읽기 쉽습니다."
                      label="Route Key"
                      placeholder="post_comments"
                      radius="xl"
                      value={createForm.routeKey}
                      onChange={(event) => setCreateForm((current) => ({ ...current, routeKey: event.currentTarget.value }))}
                    />
                    <TextInput
                      disabled={!canManage}
                      description="값이 클수록 먼저 매칭합니다. 더 구체적인 규칙에 높은 우선순위를 두는 편이 안전합니다."
                      label="Priority"
                      radius="xl"
                      type="number"
                      value={createForm.priority}
                      onChange={(event) => setCreateForm((current) => ({ ...current, priority: event.currentTarget.value }))}
                    />
                    <Button
                      disabled={!canManage}
                      leftSection={<IconPlus size={18} />}
                      loading={isSubmitting}
                      radius="xl"
                      onClick={handleCreate}
                    >
                      추가
                    </Button>
                  </Group>
                </Stack>
              </Paper>

              <Table highlightOnHover>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Template</Table.Th>
                    <Table.Th>Route Key</Table.Th>
                    <Table.Th>Priority</Table.Th>
                    <Table.Th>활성</Table.Th>
                    <Table.Th style={{ textAlign: "right" }}>작업</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {data.items.map((item) => (
                    <Table.Tr key={item.id}>
                      <Table.Td>{item.template}</Table.Td>
                      <Table.Td>{item.routeKey}</Table.Td>
                      <Table.Td>{item.priority}</Table.Td>
                      <Table.Td>
                        <Switch
                          checked={item.active}
                          disabled={!canManage}
                          onChange={(event) => void handleToggleActive(item, event.currentTarget.checked)}
                        />
                      </Table.Td>
                      <Table.Td>
                        <Group gap="xs" justify="flex-end">
                          <Button
                            disabled={!canManage}
                            leftSection={<IconEdit size={16} />}
                            radius="xl"
                            size="xs"
                            variant="default"
                            onClick={() => openEdit(item)}
                          >
                            수정
                          </Button>
                          <Button
                            color="red"
                            disabled={!canManage}
                            leftSection={<IconTrash size={16} />}
                            radius="xl"
                            size="xs"
                            variant="light"
                            onClick={() => void handleDelete(item)}
                          >
                            삭제
                          </Button>
                        </Group>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}
