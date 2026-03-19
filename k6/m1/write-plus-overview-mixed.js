import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY;
const SCENARIO = __ENV.SCENARIO || 'M1';
const RUN_ID = __ENV.RUN_ID || `m1-manual-${Date.now()}`;
const RUN_PHASE = __ENV.RUN_PHASE || 'main';
const DURATION = __ENV.DURATION || '10m';

const WRITE_RATE = Number(__ENV.WRITE_RATE || '10');
const READ_RATE = Number(__ENV.READ_RATE || '10');
const WRITE_PRE_ALLOCATED_VUS = Number(__ENV.WRITE_PRE_ALLOCATED_VUS || '20');
const WRITE_MAX_VUS = Number(__ENV.WRITE_MAX_VUS || '60');
const READ_PRE_ALLOCATED_VUS = Number(__ENV.READ_PRE_ALLOCATED_VUS || '20');
const READ_MAX_VUS = Number(__ENV.READ_MAX_VUS || '60');

const WRITE_PATH_PREFIX = normalizePathPrefix(__ENV.WRITE_PATH_PREFIX || '/loadtest/m1');
const WRITE_PATH_COUNT = Number(__ENV.WRITE_PATH_COUNT || '6');
const WRITE_EVENT_TYPE = __ENV.WRITE_EVENT_TYPE || 'loadtest_mixed';
const WRITE_EXISTING_USER_POOL_SIZE = Number(__ENV.WRITE_EXISTING_USER_POOL_SIZE || '1000');
const WRITE_EXISTING_USER_RATIO = Number(__ENV.WRITE_EXISTING_USER_RATIO || '80');

const READ_FROM = __ENV.READ_FROM;
const READ_TO = __ENV.READ_TO;
const READ_EXTERNAL_USER_ID = __ENV.READ_EXTERNAL_USER_ID || '';
const READ_EVENT_TYPE_FILTER = __ENV.READ_EVENT_TYPE_FILTER || '';

const THRESHOLDS_ENABLED = (__ENV.THRESHOLDS_ENABLED || 'true') !== 'false';
const WRITE_P95_THRESHOLD_MS = Number(__ENV.WRITE_P95_THRESHOLD_MS || '5000');
const WRITE_P99_THRESHOLD_MS = Number(__ENV.WRITE_P99_THRESHOLD_MS || '8000');
const READ_P95_THRESHOLD_MS = Number(__ENV.READ_P95_THRESHOLD_MS || '1500');
const READ_P99_THRESHOLD_MS = Number(__ENV.READ_P99_THRESHOLD_MS || '3000');

const PATH_SUFFIXES = [
  'page/1',
  'page/2',
  'post/1',
  'post/2',
  'product/10',
  'product/11',
];

const writeReqDuration = new Trend('mixed_write_req_duration');
const readReqDuration = new Trend('mixed_read_req_duration');
const writeReqFailed = new Rate('mixed_write_req_failed');
const readReqFailed = new Rate('mixed_read_req_failed');

export const options = {
  scenarios: {
    m1_write: {
      executor: 'constant-arrival-rate',
      exec: 'writeScenario',
      rate: WRITE_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: WRITE_PRE_ALLOCATED_VUS,
      maxVUs: WRITE_MAX_VUS,
    },
    m1_read: {
      executor: 'constant-arrival-rate',
      exec: 'readScenario',
      rate: READ_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: READ_PRE_ALLOCATED_VUS,
      maxVUs: READ_MAX_VUS,
    },
  },
  thresholds: THRESHOLDS_ENABLED
    ? {
        mixed_write_req_failed: ['rate<0.01'],
        mixed_read_req_failed: ['rate<0.01'],
        mixed_write_req_duration: [`p(95)<${WRITE_P95_THRESHOLD_MS}`, `p(99)<${WRITE_P99_THRESHOLD_MS}`],
        mixed_read_req_duration: [`p(95)<${READ_P95_THRESHOLD_MS}`, `p(99)<${READ_P99_THRESHOLD_MS}`],
      }
    : {},
  tags: {
    perf_scenario: SCENARIO,
    perf_run_id: RUN_ID,
    perf_phase: RUN_PHASE,
  },
};

export function writeScenario() {
  if (!API_KEY) {
    throw new Error('API_KEY environment variable is required');
  }

  const sequence = buildSequence(__VU, __ITER);
  const externalUserId = buildExternalUserId(sequence, __VU, __ITER);
  const requestId = `k6-${RUN_ID}-${RUN_PHASE}-write-vu${__VU}-it${__ITER}`;
  const body = JSON.stringify({
    externalUserId,
    eventType: WRITE_EVENT_TYPE,
    path: buildPath(sequence),
    occurredAt: new Date().toISOString(),
    payload: JSON.stringify({
      source: 'k6-m1',
      scenario: SCENARIO,
      phase: RUN_PHASE,
      runId: RUN_ID,
      requestId,
      userKind: isExistingUser(sequence) ? 'existing' : 'new',
      vu: __VU,
      iter: __ITER,
      sequence,
    }),
  });

  const response = http.post(`${BASE_URL}/api/events`, body, {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
      'X-Request-Id': requestId,
    },
    tags: {
      request_kind: 'write',
    },
  });

  writeReqDuration.add(response.timings.duration);
  writeReqFailed.add(response.status !== 200);

  check(response, {
    'write status is 200': (res) => res.status === 200,
    'write response has id': (res) => {
      if (res.status !== 200) {
        return false;
      }

      try {
        const json = res.json();
        return json && Number.isInteger(json.id);
      } catch (error) {
        return false;
      }
    },
  });
}

export function readScenario() {
  if (!API_KEY) {
    throw new Error('API_KEY environment variable is required');
  }

  if (!READ_FROM || !READ_TO) {
    throw new Error('READ_FROM and READ_TO environment variables are required');
  }

  const requestId = `k6-${RUN_ID}-${RUN_PHASE}-read-vu${__VU}-it${__ITER}`;
  const params = [
    `from=${encodeURIComponent(READ_FROM)}`,
    `to=${encodeURIComponent(READ_TO)}`,
  ];

  if (READ_EXTERNAL_USER_ID) {
    params.push(`externalUserId=${encodeURIComponent(READ_EXTERNAL_USER_ID)}`);
  }

  if (READ_EVENT_TYPE_FILTER) {
    params.push(`eventType=${encodeURIComponent(READ_EVENT_TYPE_FILTER)}`);
  }

  const url = `${BASE_URL}/api/v1/events/analytics/aggregates/overview?${params.join('&')}`;
  const response = http.get(url, {
    headers: {
      'X-API-Key': API_KEY,
      'X-Request-Id': requestId,
    },
    tags: {
      request_kind: 'read',
    },
  });

  readReqDuration.add(response.timings.duration);
  readReqFailed.add(response.status !== 200);

  check(response, {
    'read status is 200': (res) => res.status === 200,
    'read response has totalEvents': (res) => {
      if (res.status !== 200) {
        return false;
      }

      try {
        const json = res.json();
        return json && typeof json.totalEvents === 'number';
      } catch (error) {
        return false;
      }
    },
  });
}

function normalizePathPrefix(value) {
  return value.replace(/\/+$/, '');
}

function buildSequence(vu, iter) {
  return (vu - 1) * 1000000 + iter;
}

function isExistingUser(sequence) {
  return sequence % 100 < WRITE_EXISTING_USER_RATIO;
}

function buildExternalUserId(sequence, vu, iter) {
  if (isExistingUser(sequence)) {
    return `m1-user-${String((sequence % WRITE_EXISTING_USER_POOL_SIZE) + 1).padStart(4, '0')}`;
  }

  return `lt-${RUN_ID}-${RUN_PHASE}-new-${vu}-${iter}`;
}

function buildPath(sequence) {
  const suffix = PATH_SUFFIXES[sequence % Math.min(WRITE_PATH_COUNT, PATH_SUFFIXES.length)];
  return `${WRITE_PATH_PREFIX}/${suffix}`;
}
