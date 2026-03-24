import http from 'k6/http';
import { check } from 'k6';
import {
  buildRequestId,
  buildRequestParams,
  buildSequence,
  buildUrl,
  expectResponseShape,
  parseJsonEnv,
  pickOrg,
  pickProfile,
  requireNonEmptyArray,
} from './common.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO || 'R4';
const RUN_ID = __ENV.RUN_ID || `r4-manual-${Date.now()}`;
const RUN_PHASE = __ENV.RUN_PHASE || 'main';
const RATE = Number(__ENV.RATE || '10');
const DURATION = __ENV.DURATION || '5m';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || '20');
const MAX_VUS = Number(__ENV.MAX_VUS || '60');
const THRESHOLDS_ENABLED = (__ENV.THRESHOLDS_ENABLED || 'true') !== 'false';
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || '3000');
const P99_THRESHOLD_MS = Number(__ENV.P99_THRESHOLD_MS || '5000');
const ORGS = parseJsonEnv('ORGS_JSON', []);
const PROFILES = parseJsonEnv('PROFILES_JSON', []);

requireNonEmptyArray('ORGS_JSON', ORGS);
requireNonEmptyArray('PROFILES_JSON', PROFILES);

export const options = {
  scenarios: {
    v2_read_mix: {
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
  const profile = pickProfile(PROFILES, sequence * 7);
  const requestId = buildRequestId(RUN_ID, RUN_PHASE, 'read', __VU, __ITER);
  const params = buildRequestParams(org.apiKey, requestId, {
    org_key: org.key,
    org_tier: org.tier,
    request_kind: 'read',
    request_profile: profile.name,
  }, profile.method === 'POST');

  let response;
  if (profile.method === 'POST') {
    response = http.post(`${BASE_URL}${profile.path}`, JSON.stringify(profile.body || {}), params);
  } else {
    response = http.get(buildUrl(BASE_URL, profile.path, profile.query), params);
  }

  check(response, {
    'status is 200': (res) => res.status === 200,
    'response matches profile': (res) => expectResponseShape(res, profile.expect),
  });
}
