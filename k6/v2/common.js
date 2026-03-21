const PATHS_BY_GROUP = {
  browse: [
    '/home',
    '/docs',
    '/blog',
    '/posts/10',
    '/posts/10/comments',
    '/products/8',
  ],
  product: [
    '/dashboard',
    '/projects/1',
    '/projects/1/overview',
    '/projects/1/routes',
    '/teams/1',
    '/teams/1/projects',
    '/products/8/reviews',
  ],
  conversion: [
    '/pricing',
    '/signup',
    '/checkout',
  ],
};

const RAW_EVENT_TYPES_BY_GROUP = {
  browse: [
    { value: 'page_view', weight: 70 },
    { value: 'button_click', weight: 26 },
    { value: 'signup_submit', weight: 3 },
    { value: 'purchase_complete', weight: 1 },
  ],
  product: [
    { value: 'page_view', weight: 45 },
    { value: 'button_click', weight: 35 },
    { value: 'signup_submit', weight: 12 },
    { value: 'purchase_complete', weight: 8 },
  ],
  conversion: [
    { value: 'page_view', weight: 30 },
    { value: 'button_click', weight: 20 },
    { value: 'signup_submit', weight: 30 },
    { value: 'purchase_complete', weight: 20 },
  ],
};

export function parseJsonEnv(name, fallback) {
  const raw = __ENV[name];
  if (!raw) {
    return fallback;
  }

  try {
    return JSON.parse(raw);
  } catch (error) {
    throw new Error(`${name} must be valid JSON`);
  }
}

export function requireNonEmptyArray(name, value) {
  if (!Array.isArray(value) || value.length === 0) {
    throw new Error(`${name} must be a non-empty array`);
  }
}

export function buildSequence(vu, iter) {
  return (vu - 1) * 1000000 + iter;
}

export function buildRequestId(runId, phase, kind, vu, iter) {
  return `k6-${runId}-${phase}-${kind}-vu${vu}-it${iter}`;
}

export function pickOrg(orgs, seed) {
  return weightedPick(orgs, 'sharePct', seed + 17);
}

export function pickProfile(profiles, seed) {
  return weightedPick(profiles, 'weight', seed + 31);
}

export function buildUrl(baseUrl, path, query) {
  const queryString = buildQueryString(query);
  return queryString ? `${baseUrl}${path}?${queryString}` : `${baseUrl}${path}`;
}

export function buildRequestParams(apiKey, requestId, tags, jsonBody = false) {
  const headers = {
    'X-API-Key': apiKey,
    'X-Request-Id': requestId,
  };

  if (jsonBody) {
    headers['Content-Type'] = 'application/json';
  }

  return { headers, tags };
}

export function expectResponseShape(response, expectKind) {
  if (response.status !== 200) {
    return false;
  }

  let json;
  try {
    json = response.json();
  } catch (error) {
    return false;
  }

  switch (expectKind) {
    case 'overview':
      return json && typeof json.totalEvents === 'number' && Array.isArray(json.topRoutes);
    case 'items':
      return json && Array.isArray(json.items);
    case 'usersOverview':
      return json && typeof json.identifiedUsers === 'number';
    case 'funnelReport':
      return json && Array.isArray(json.steps) && Array.isArray(json.items);
    case 'dailyRetention':
      return json && Array.isArray(json.items);
    case 'retentionMatrix':
      return json && Array.isArray(json.days) && Array.isArray(json.items);
    case 'writeCreate':
      return json && Number.isInteger(json.id);
    default:
      return json != null;
  }
}

export function buildWriteBody(org, requestConfig, sequence, vu, iter, runId, phase) {
  const payloadDistributions = requestConfig.payloadDistributions || {};
  const pathGroup = weightedPickFromMap(payloadDistributions.pathGroups || { browse: 60, product: 25, conversion: 15 }, seedFloat(sequence + 101));
  const path = pickFromArray(PATHS_BY_GROUP[pathGroup], sequence + 151);
  const eventType = weightedPick(RAW_EVENT_TYPES_BY_GROUP[pathGroup], 'weight', sequence + 211).value;
  const userShape = resolveUserShape(org, requestConfig, sequence, vu, iter, runId, phase);

  return {
    externalUserId: userShape.externalUserId,
    eventType,
    path,
    occurredAt: new Date().toISOString(),
    payload: JSON.stringify({
      source: 'k6-v2',
      runId,
      phase,
      orgKey: org.key,
      orgTier: org.tier,
      userKind: userShape.userKind,
      userScope: userShape.userScope,
      pathGroup,
      vu,
      iter,
      sequence,
    }),
  };
}

function resolveUserShape(org, requestConfig, sequence, vu, iter, runId, phase) {
  const payloadDistributions = requestConfig.payloadDistributions || {};
  const identifiedVsAnonymous = payloadDistributions.identifiedVsAnonymous || { identified: 70, anonymous: 30 };
  const identifiedExistingVsNew = payloadDistributions.identifiedExistingVsNew || { existing: 80, new: 20 };

  const userScope = weightedPickFromMap(identifiedVsAnonymous, seedFloat(sequence + 41));
  if (userScope === 'anonymous') {
    return {
      userScope,
      userKind: 'anonymous',
      externalUserId: null,
    };
  }

  const userKind = weightedPickFromMap(identifiedExistingVsNew, seedFloat(sequence + 59));
  if (userKind === 'existing') {
    const poolSize = Number(org.identifiedUsers || 1);
    const userNumber = (sequence % Math.max(poolSize, 1)) + 1;
    return {
      userScope,
      userKind,
      externalUserId: `${org.key}-user-${String(userNumber).padStart(5, '0')}`,
    };
  }

  return {
    userScope,
    userKind,
    externalUserId: `${org.key}-new-${runId}-${phase}-${vu}-${iter}`,
  };
}

function buildQueryString(query) {
  if (!query || typeof query !== 'object') {
    return '';
  }

  return Object.entries(query)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&');
}

function weightedPick(items, weightKey, seed) {
  const total = items.reduce((sum, item) => sum + Number(item[weightKey] || 0), 0);
  if (total <= 0) {
    return items[0];
  }

  let cursor = seedFloat(seed) * total;
  for (const item of items) {
    cursor -= Number(item[weightKey] || 0);
    if (cursor <= 0) {
      return item;
    }
  }

  return items[items.length - 1];
}

function weightedPickFromMap(weightMap, seed) {
  const entries = Object.entries(weightMap).map(([key, value]) => ({ key, weight: Number(value) }));
  return weightedPick(entries, 'weight', seed * 1000000).key;
}

function pickFromArray(values, seed) {
  if (!Array.isArray(values) || values.length === 0) {
    throw new Error('pickFromArray requires a non-empty array');
  }

  const index = Math.floor(seedFloat(seed) * values.length) % values.length;
  return values[index];
}

function seedFloat(seed) {
  let value = Number(seed) >>> 0;
  value += 0x6d2b79f5;
  value = Math.imul(value ^ (value >>> 15), value | 1);
  value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
  return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
}
