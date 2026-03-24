import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import {
  buildRequestId,
  buildRequestParams,
  buildSequence,
  buildUrl,
  buildWriteBody,
  expectResponseShape,
  parseJsonEnv,
  pickOrg,
  pickProfile,
  requireNonEmptyArray,
} from './common.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO || 'M2';
const RUN_ID = __ENV.RUN_ID || `m2-manual-${Date.now()}`;
const RUN_PHASE = __ENV.RUN_PHASE || 'main';
const DURATION = __ENV.DURATION || '5m';

const WRITE_RATE = Number(__ENV.WRITE_RATE || '95');
const READ_RATE = Number(__ENV.READ_RATE || '5');
const WRITE_PRE_ALLOCATED_VUS = Number(__ENV.WRITE_PRE_ALLOCATED_VUS || '200');
const WRITE_MAX_VUS = Number(__ENV.WRITE_MAX_VUS || '500');
const READ_PRE_ALLOCATED_VUS = Number(__ENV.READ_PRE_ALLOCATED_VUS || '20');
const READ_MAX_VUS = Number(__ENV.READ_MAX_VUS || '60');

const THRESHOLDS_ENABLED = (__ENV.THRESHOLDS_ENABLED || 'true') !== 'false';
const WRITE_P95_THRESHOLD_MS = Number(__ENV.WRITE_P95_THRESHOLD_MS || '5000');
const WRITE_P99_THRESHOLD_MS = Number(__ENV.WRITE_P99_THRESHOLD_MS || '8000');
const READ_P95_THRESHOLD_MS = Number(__ENV.READ_P95_THRESHOLD_MS || '5000');
const READ_P99_THRESHOLD_MS = Number(__ENV.READ_P99_THRESHOLD_MS || '8000');

const ORGS = parseJsonEnv('ORGS_JSON', []);
const WRITE_REQUEST = parseJsonEnv('WRITE_REQUEST_JSON', {});
const READ_PROFILES = parseJsonEnv('READ_PROFILES_JSON', []);

requireNonEmptyArray('ORGS_JSON', ORGS);
requireNonEmptyArray('READ_PROFILES_JSON', READ_PROFILES);

const writeReqDuration = new Trend('mixed_write_req_duration');
const readReqDuration = new Trend('mixed_read_req_duration');
const writeReqFailed = new Rate('mixed_write_req_failed');
const readReqFailed = new Rate('mixed_read_req_failed');

export const options = {
  scenarios: {
    m2_write: {
      executor: 'constant-arrival-rate',
      exec: 'writeScenario',
      rate: WRITE_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: WRITE_PRE_ALLOCATED_VUS,
      maxVUs: WRITE_MAX_VUS,
    },
    m2_read: {
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
  const sequence = buildSequence(__VU, __ITER);
  const org = pickOrg(ORGS, sequence);
  const requestId = buildRequestId(RUN_ID, RUN_PHASE, 'write', __VU, __ITER);
  const body = JSON.stringify(buildWriteBody(org, WRITE_REQUEST, sequence, __VU, __ITER, RUN_ID, RUN_PHASE));

  const response = http.post(
    `${BASE_URL}/api/events`,
    body,
    buildRequestParams(org.apiKey, requestId, {
      org_key: org.key,
      org_tier: org.tier,
      request_kind: 'write',
    }, true),
  );

  writeReqDuration.add(response.timings.duration);
  writeReqFailed.add(response.status !== 200);

  check(response, {
    'write status is 200': (res) => res.status === 200,
    'write response matches shape': (res) => expectResponseShape(res, 'writeCreate'),
  });
}

export function readScenario() {
  const sequence = buildSequence(__VU, __ITER);
  const org = pickOrg(ORGS, sequence);
  const profile = pickProfile(READ_PROFILES, sequence * 7);
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

  readReqDuration.add(response.timings.duration);
  readReqFailed.add(response.status !== 200);

  check(response, {
    'read status is 200': (res) => res.status === 200,
    'read response matches profile': (res) => expectResponseShape(res, profile.expect),
  });
}
