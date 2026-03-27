"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import { fetchMe } from "@/lib/api/auth";
import {
  createEventTypeMapping,
  deleteEventTypeMapping,
  fetchEventTypeMappings,
  updateEventTypeMapping,
  updateEventTypeMappingActive,
} from "@/lib/api/rules";
import { getAccessToken } from "@/lib/session/token-store";
import type {
  EventTypeMappingCreateRequest,
  EventTypeMappingItem,
  EventTypeMappingUpdateRequest,
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

type EventTypeMappingPageState = {
  organizationName: string;
  role: string | null;
  items: EventTypeMappingItem[];
};

type EventTypeMappingFormState = {
  rawEventType: string;
  canonicalEventType: string;
};

const EMPTY_FORM: EventTypeMappingFormState = {
  rawEventType: "",
  canonicalEventType: "",
};

export default function EventTypeMappingsPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<EventTypeMappingPageState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitErrorMessage, setSubmitErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isEditOpened, setIsEditOpened] = useState(false);
  const [editingItem, setEditingItem] = useState<EventTypeMappingItem | null>(null);
  const [createForm, setCreateForm] = useState<EventTypeMappingFormState>(EMPTY_FORM);
  const [editForm, setEditForm] = useState<EventTypeMappingFormState>(EMPTY_FORM);

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
          fetchEventTypeMappings(accessToken, params.organizationId),
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
        setErrorMessage(error instanceof Error ? error.message : "event type mapping 목록을 불러오지 못했습니다.");
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [params.organizationId, router]);

  const canManage = data?.role === "ADMIN" || data?.role === "OWNER";

  function parseForm(form: EventTypeMappingFormState): EventTypeMappingCreateRequest | EventTypeMappingUpdateRequest {
    return {
      rawEventType: form.rawEventType.trim(),
      canonicalEventType: form.canonicalEventType.trim(),
    };
  }

  function openEdit(item: EventTypeMappingItem) {
    setEditingItem(item);
    setEditForm({
      rawEventType: item.rawEventType,
      canonicalEventType: item.canonicalEventType,
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
      const created = await createEventTypeMapping(accessToken, params.organizationId, parseForm(createForm));
      setData({
        ...data,
        items: [...data.items, created].sort((a, b) => a.rawEventType.localeCompare(b.rawEventType)),
      });
      setCreateForm(EMPTY_FORM);
    } catch (error) {
      setSubmitErrorMessage(error instanceof Error ? error.message : "event type mapping을 추가하지 못했습니다.");
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
      const updated = await updateEventTypeMapping(
        accessToken,
        params.organizationId,
        editingItem.id,
        parseForm(editForm),
      );
      setData({
        ...data,
        items: data.items
          .map((item) => (item.id === updated.id ? updated : item))
          .sort((a, b) => a.rawEventType.localeCompare(b.rawEventType)),
      });
      setIsEditOpened(false);
      setEditingItem(null);
    } catch (error) {
      setSubmitErrorMessage(error instanceof Error ? error.message : "event type mapping을 수정하지 못했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleToggleActive(item: EventTypeMappingItem, active: boolean) {
    const accessToken = getAccessToken();
    if (!accessToken || !data) return;

    try {
      const updated = await updateEventTypeMappingActive(accessToken, params.organizationId, item.id, active);
      setData({
        ...data,
        items: data.items.map((current) => (current.id === updated.id ? updated : current)),
      });
    } catch (error) {
      setSubmitErrorMessage(error instanceof Error ? error.message : "event type mapping 상태를 바꾸지 못했습니다.");
    }
  }

  async function handleDelete(item: EventTypeMappingItem) {
    const accessToken = getAccessToken();
    if (!accessToken || !data) return;

    try {
      await deleteEventTypeMapping(accessToken, params.organizationId, item.id);
      setData({
        ...data,
        items: data.items.filter((current) => current.id !== item.id),
      });
    } catch (error) {
      setSubmitErrorMessage(error instanceof Error ? error.message : "event type mapping을 삭제하지 못했습니다.");
    }
  }

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Event Type Mapping 관리"
          subtitle="canonical event type 규칙 목록을 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}/event-types`}
          badge="Settings"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">event type mapping 목록을 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage || !data) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Event Type Mapping 관리"
          subtitle="canonical event type 규칙 테이블을 확인합니다."
          backHref={`/dashboard/${params.organizationId}/event-types`}
          badge="Settings"
        />
        <Container size="md" py={96}>
          <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
            {errorMessage ?? "event type mapping 목록을 불러오지 못했습니다."}
          </Alert>
        </Container>
      </ConsoleFrame>
    );
  }

  return (
    <ConsoleFrame>
      <ConsoleHeader
        title={`${data.organizationName} Event Type Mapping 관리`}
        subtitle="raw event type를 canonical event type으로 정규화하는 규칙을 조회하고 수정합니다."
        backHref={`/dashboard/${params.organizationId}/event-types`}
        badge="Settings"
      />
      <Modal
        centered
        opened={isEditOpened}
        radius="28px"
        title="Event Type Mapping 수정"
        onClose={() => setIsEditOpened(false)}
      >
        <Stack gap="md">
          <TextInput
            description="수집된 원본 이벤트 이름입니다. SDK나 클라이언트에서 실제로 보내는 값을 그대로 적습니다."
            label="Raw Event Type"
            radius="xl"
            value={editForm.rawEventType}
            onChange={(event) => setEditForm((current) => ({ ...current, rawEventType: event.currentTarget.value }))}
          />
          <TextInput
            description="집계에서 공통으로 사용할 대표 이름입니다. 유사한 raw event type을 같은 키로 묶을 때 사용합니다."
            label="Canonical Event Type"
            radius="xl"
            value={editForm.canonicalEventType}
            onChange={(event) => setEditForm((current) => ({ ...current, canonicalEventType: event.currentTarget.value }))}
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
                  <Badge color="teal" mb="md" variant="light">
                    Event Type Rules
                  </Badge>
                  <Title order={1}>Canonical Event Type 테이블</Title>
                  <Text c="dimmed" mt="sm">
                    raw event type를 어떤 canonical event type으로 묶을지 관리합니다.
                  </Text>
                </div>
                <Stack gap="xs" align="flex-end">
                  <Badge color="dark" radius="xl" variant="light">
                    {data.items.length} mappings
                  </Badge>
                  <Badge color={canManage ? "teal" : "gray"} radius="xl" variant="light">
                    {canManage ? "ADMIN 이상 수정 가능" : "VIEWER는 조회만 가능"}
                  </Badge>
                </Stack>
              </Group>

              {submitErrorMessage ? (
                <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
                  {submitErrorMessage}
                </Alert>
              ) : null}

              <Paper p="lg" radius="24px" withBorder>
                <Stack gap="md">
                  <Text fw={700}>새 canonical event type 규칙 추가</Text>
                  <Group align="flex-end" grow>
                    <TextInput
                      disabled={!canManage}
                      description="수집된 원본 이벤트 이름입니다. SDK나 클라이언트에서 실제로 보내는 값을 그대로 적습니다."
                      label="Raw Event Type"
                      placeholder="button_click"
                      radius="xl"
                      value={createForm.rawEventType}
                      onChange={(event) => setCreateForm((current) => ({ ...current, rawEventType: event.currentTarget.value }))}
                    />
                    <TextInput
                      disabled={!canManage}
                      description="집계에서 공통으로 사용할 대표 이름입니다. 유사한 raw event type을 같은 키로 묶을 때 사용합니다."
                      label="Canonical Event Type"
                      placeholder="CLICK"
                      radius="xl"
                      value={createForm.canonicalEventType}
                      onChange={(event) => setCreateForm((current) => ({ ...current, canonicalEventType: event.currentTarget.value }))}
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
                    <Table.Th>Raw Event Type</Table.Th>
                    <Table.Th>Canonical Event Type</Table.Th>
                    <Table.Th>활성</Table.Th>
                    <Table.Th style={{ textAlign: "right" }}>작업</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {data.items.map((item) => (
                    <Table.Tr key={item.id}>
                      <Table.Td>{item.rawEventType}</Table.Td>
                      <Table.Td>{item.canonicalEventType}</Table.Td>
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
