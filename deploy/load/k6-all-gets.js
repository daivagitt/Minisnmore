/**
 * k6 load test — all resolvable GET endpoints (see prepare-k6-urls.py).
 * Env: BASE_URL, K6_URLS_FILE, K6_MAX_RESPONSE_MS, K6_ADMIN_TOKEN, K6_CUSTOMER_TOKEN,
 *      K6_VUS, K6_DURATION, K6_ENDPOINT_REPORT
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const maxMs = Number(__ENV.K6_MAX_RESPONSE_MS || 3000);

const payload = new SharedArray('k6-payload', function () {
  const raw = open(__ENV.K6_URLS_FILE);
  return [JSON.parse(raw)];
});

const endpoints = payload[0].endpoints || [];
const defaultBase = payload[0].baseUrl || __ENV.BASE_URL || '';

// k6 only exports tagged sub-metrics that have a threshold, so each endpoint gets an always-true one.
const endpointThresholds = {};
for (const ep of endpoints) {
  const label = endpointLabel(ep);
  endpointThresholds[`http_req_duration{endpoint:${label}}`] = ['max>=0'];
  endpointThresholds[`http_req_failed{endpoint:${label}}`] = ['rate>=0'];
  endpointThresholds[`http_reqs{endpoint:${label}}`] = ['count>=0'];
}

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: [`p(95)<${maxMs}`],
    ...endpointThresholds,
  },
};

function authHeader(kind) {
  if (kind === 'admin' && __ENV.K6_ADMIN_TOKEN) {
    return { Authorization: `Bearer ${__ENV.K6_ADMIN_TOKEN}` };
  }
  if (kind === 'customer' && __ENV.K6_CUSTOMER_TOKEN) {
    return { Authorization: `Bearer ${__ENV.K6_CUSTOMER_TOKEN}` };
  }
  return {};
}

function endpointLabel(ep) {
  return `${ep.method || 'GET'} ${ep.openapiPath || ep.path}`;
}

export default function () {
  if (!endpoints.length) {
    throw new Error('No endpoints in K6_URLS_FILE');
  }
  const idx = (__VU + __ITER) % endpoints.length;
  const ep = endpoints[idx];
  const base = (__ENV.BASE_URL || defaultBase).replace(/\/$/, '');
  const url = `${base}${ep.path}`;
  const label = endpointLabel(ep);
  const headers = {
    Accept: 'application/json',
    ...authHeader(ep.auth),
  };
  const res = http.get(url, { headers, tags: { endpoint: label } });
  check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
    [`response < ${maxMs}ms`]: (r) => r.timings.duration < maxMs,
  });
  sleep(0.05);
}

function metricValues(metric) {
  if (!metric) return {};
  return metric.values || metric;
}

function taggedMetricKeys(metrics, prefix) {
  const re = new RegExp(`^${prefix}\\{endpoint:([^}]+)\\}$`);
  const out = [];
  for (const key of Object.keys(metrics || {})) {
    const match = key.match(re);
    if (match) {
      out.push({ key, endpoint: match[1] });
    }
  }
  return out;
}

function buildEndpointRows(data) {
  const metrics = data.metrics || {};
  const rows = [];
  for (const { key, endpoint } of taggedMetricKeys(metrics, 'http_req_duration')) {
    const duration = metricValues(metrics[key]);
    const failedKey = `http_req_failed{endpoint:${endpoint}}`;
    const reqsKey = `http_reqs{endpoint:${endpoint}}`;
    const failed = metricValues(metrics[failedKey]);
    const reqs = metricValues(metrics[reqsKey]);
    const failRate = failed && typeof failed.rate === 'number' ? failed.rate : failed && failed.value;
    rows.push({
      endpoint,
      requests: reqs && typeof reqs.count === 'number' ? reqs.count : reqs && reqs.value,
      avgMs: duration.avg != null ? Math.round(duration.avg) : null,
      p95Ms: duration['p(95)'] != null ? Math.round(duration['p(95)']) : null,
      maxMs: duration.max != null ? Math.round(duration.max) : null,
      failRatePct: failRate != null ? Math.round(failRate * 10000) / 100 : null,
    });
  }
  rows.sort((a, b) => a.endpoint.localeCompare(b.endpoint));
  return rows;
}

export function handleSummary(data) {
  const vus = Number(__ENV.K6_VUS || 0);
  const report = {
    vus,
    duration: __ENV.K6_DURATION || '',
    endpointCount: buildEndpointRows(data).length,
    endpoints: buildEndpointRows(data),
  };
  const outPath = __ENV.K6_ENDPOINT_REPORT;
  if (outPath) {
    return { [outPath]: JSON.stringify(report, null, 2) };
  }
  return {};
}
