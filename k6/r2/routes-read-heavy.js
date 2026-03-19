import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY;
const SCENARIO = __ENV.SCENARIO || 'R2';
const RUN_ID = __ENV.RUN_ID || `r2-manual-${Date.now()}`;
const RUN_PHASE = __ENV.RUN_PHASE || 'main';
const RATE = Number(__ENV.RATE || '10');
const DURATION = __ENV.DURATION || '10m';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || '20');
const MAX_VUS = Number(__ENV.MAX_VUS || '60');
const FROM = __ENV.FROM;
const TO = __ENV.TO;
const TOP = Number(__ENV.TOP || '10');
const EXTERNAL_USER_ID = __ENV.EXTERNAL_USER_ID || '';
const EVENT_TYPE_FILTER = __ENV.EVENT_TYPE_FILTER || '';
const THRESHOLDS_ENABLED = (__ENV.THRESHOLDS_ENABLED || 'true') !== 'false';
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || '1500');
const P99_THRESHOLD_MS = Number(__ENV.P99_THRESHOLD_MS || '3000');

export const options = {
  scenarios: {
    r2_routes_read_heavy: {
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

  if (!FROM || !TO) {
    throw new Error('FROM and TO environment variables are required');
  }

  const requestId = `k6-${RUN_ID}-${RUN_PHASE}-vu${__VU}-it${__ITER}`;
  const params = [
    `from=${encodeURIComponent(FROM)}`,
    `to=${encodeURIComponent(TO)}`,
    `top=${encodeURIComponent(String(TOP))}`,
  ];

  if (EXTERNAL_USER_ID) {
    params.push(`externalUserId=${encodeURIComponent(EXTERNAL_USER_ID)}`);
  }

  if (EVENT_TYPE_FILTER) {
    params.push(`eventType=${encodeURIComponent(EVENT_TYPE_FILTER)}`);
  }

  const url = `${BASE_URL}/api/v1/events/analytics/aggregates/routes?${params.join('&')}`;
  const response = http.get(url, {
    headers: {
      'X-API-Key': API_KEY,
      'X-Request-Id': requestId,
    },
  });

  check(response, {
    'status is 200': (res) => res.status === 200,
    'response has items': (res) => {
      if (res.status !== 200) {
        return false;
      }

      try {
        const json = res.json();
        return json && Array.isArray(json.items);
      } catch (error) {
        return false;
      }
    },
    'items size is within top': (res) => {
      if (res.status !== 200) {
        return false;
      }

      try {
        const json = res.json();
        return json && Array.isArray(json.items) && json.items.length <= TOP;
      } catch (error) {
        return false;
      }
    },
  });
}
