import http from 'k6/http';
import { check } from 'k6';
import {
  buildRequestId,
  buildRequestParams,
  buildSequence,
  buildWriteBody,
  expectResponseShape,
  parseJsonEnv,
  pickOrg,
  requireNonEmptyArray,
} from './common.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO || 'W2';
const RUN_ID = __ENV.RUN_ID || `w2-manual-${Date.now()}`;
const RUN_PHASE = __ENV.RUN_PHASE || 'main';
const RATE = Number(__ENV.RATE || '100');
const DURATION = __ENV.DURATION || '5m';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || '200');
const MAX_VUS = Number(__ENV.MAX_VUS || '500');
const THRESHOLDS_ENABLED = (__ENV.THRESHOLDS_ENABLED || 'true') !== 'false';
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || '5000');
const P99_THRESHOLD_MS = Number(__ENV.P99_THRESHOLD_MS || '8000');
const ORGS = parseJsonEnv('ORGS_JSON', []);
const REQUEST = parseJsonEnv('REQUEST_JSON', {});

requireNonEmptyArray('ORGS_JSON', ORGS);

export const options = {
  scenarios: {
    v2_write: {
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
  const sequence = buildSequence(__VU, __ITER);
  const org = pickOrg(ORGS, sequence);
  const requestId = buildRequestId(RUN_ID, RUN_PHASE, 'write', __VU, __ITER);
  const body = JSON.stringify(buildWriteBody(org, REQUEST, sequence, __VU, __ITER, RUN_ID, RUN_PHASE));

  const response = http.post(
    `${BASE_URL}/api/events`,
    body,
    buildRequestParams(org.apiKey, requestId, {
      org_key: org.key,
      org_tier: org.tier,
      request_kind: 'write',
    }, true),
  );

  check(response, {
    'status is 200': (res) => res.status === 200,
    'response matches write shape': (res) => expectResponseShape(res, 'writeCreate'),
  });
}
