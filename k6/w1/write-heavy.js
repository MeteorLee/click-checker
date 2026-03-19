import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY;
const SCENARIO = __ENV.SCENARIO || 'W1';
const RUN_ID = __ENV.RUN_ID || `w1-manual-${Date.now()}`;
const RUN_PHASE = __ENV.RUN_PHASE || 'main';
const RATE = Number(__ENV.RATE || '50');
const DURATION = __ENV.DURATION || '10m';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || '100');
const MAX_VUS = Number(__ENV.MAX_VUS || '300');
const EVENT_TYPE = __ENV.EVENT_TYPE || 'loadtest_write';
const PATH_PREFIX = normalizePathPrefix(__ENV.PATH_PREFIX || '/loadtest/w1');
const EXISTING_USER_POOL_SIZE = Number(__ENV.EXISTING_USER_POOL_SIZE || '1000');
const EXISTING_USER_RATIO = Number(__ENV.EXISTING_USER_RATIO || '80');
const THRESHOLDS_ENABLED = (__ENV.THRESHOLDS_ENABLED || 'true') !== 'false';
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || '5000');
const P99_THRESHOLD_MS = Number(__ENV.P99_THRESHOLD_MS || '8000');

const PATH_SUFFIXES = [
  'page/1',
  'page/2',
  'post/1',
  'post/2',
  'product/10',
  'product/11',
];

export const options = {
  scenarios: {
    w1_write_heavy: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: THRESHOLDS_ENABLED
    ? {
        http_req_failed: ['rate<0.01'],
        http_req_duration: [`p(95)<${P95_THRESHOLD_MS}`, `p(99)<${P99_THRESHOLD_MS}`],
      }
    : {},
  tags: {
    perf_scenario: SCENARIO,
    perf_run_id: RUN_ID,
    perf_phase: RUN_PHASE,
  },
};

export default function () {
  if (!API_KEY) {
    throw new Error('API_KEY environment variable is required');
  }

  const sequence = buildSequence(__VU, __ITER);
  const externalUserId = buildExternalUserId(sequence, __VU, __ITER);
  const requestId = `k6-${RUN_ID}-${RUN_PHASE}-vu${__VU}-it${__ITER}`;
  const body = JSON.stringify({
    externalUserId,
    eventType: EVENT_TYPE,
    path: buildPath(sequence),
    occurredAt: new Date().toISOString(),
    payload: JSON.stringify({
      source: 'k6-w1',
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
  });

  check(response, {
    'status is 200': (res) => res.status === 200,
    'response has id': (res) => {
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

function normalizePathPrefix(value) {
  return value.replace(/\/+$/, '');
}

function buildSequence(vu, iter) {
  return (vu - 1) * 1000000 + iter;
}

function isExistingUser(sequence) {
  return sequence % 100 < EXISTING_USER_RATIO;
}

function buildExternalUserId(sequence, vu, iter) {
  if (isExistingUser(sequence)) {
    return `existing-${sequence % EXISTING_USER_POOL_SIZE}`;
  }

  return `lt-${RUN_ID}-${RUN_PHASE}-new-${vu}-${iter}`;
}

function buildPath(sequence) {
  const suffix = PATH_SUFFIXES[sequence % PATH_SUFFIXES.length];
  return `${PATH_PREFIX}/${suffix}`;
}
