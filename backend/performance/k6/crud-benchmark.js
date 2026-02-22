/**
 * k6 Performance Test - CRM CRUD Endpoint Benchmark
 *
 * Validates that all CRUD endpoints meet the MVP target:
 *   p95 response time < 100ms
 *
 * Usage:
 *   k6 run crud-benchmark.js
 *   k6 run --env BASE_URL=http://localhost:8081 crud-benchmark.js
 *   k6 run --env BASE_URL=http://localhost:8080 crud-benchmark.js  # via gateway
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const createLatency = new Trend('create_latency', true);
const readLatency = new Trend('read_latency', true);
const updateLatency = new Trend('update_latency', true);
const deleteLatency = new Trend('delete_latency', true);
const listLatency = new Trend('list_latency', true);
const searchLatency = new Trend('search_latency', true);

// Test configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export const options = {
  stages: [
    { duration: '10s', target: 5 },   // ramp up
    { duration: '30s', target: 10 },   // sustained load
    { duration: '10s', target: 0 },    // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<100'],   // MVP target: p95 < 100ms
    'create_latency': ['p(95)<100'],
    'read_latency': ['p(95)<100'],
    'update_latency': ['p(95)<100'],
    'list_latency': ['p(95)<100'],
    'search_latency': ['p(95)<100'],
    'errors': ['rate<0.05'],            // less than 5% error rate
  },
};

const headers = { 'Content-Type': 'application/json' };

// -------------------------------------------------------------------
// Helper: perform a request and track result
// -------------------------------------------------------------------
function request(method, url, body, metric) {
  const params = { headers };
  let res;
  if (method === 'GET' || method === 'DELETE') {
    res = http[method.toLowerCase()](url, params);
  } else {
    res = http[method.toLowerCase()](url, JSON.stringify(body), params);
  }

  if (metric) {
    metric.add(res.timings.duration);
  }

  const ok = res.status >= 200 && res.status < 300;
  errorRate.add(!ok);
  return res;
}

// -------------------------------------------------------------------
// Scenario: Organization CRUD
// -------------------------------------------------------------------
function organizationCrud() {
  group('Organization CRUD', () => {
    // Create
    const createRes = request('POST', `${BASE_URL}/api/v1/organizations`, {
      name: `Perf Org ${Date.now()}`,
      type: 'PROSPECT',
      industry: 'Technology',
    }, createLatency);

    check(createRes, { 'org created': (r) => r.status === 200 || r.status === 201 });

    const orgBody = JSON.parse(createRes.body);
    const orgId = orgBody.data ? orgBody.data.id : orgBody.id;

    if (!orgId) return;

    // Read
    const readRes = request('GET', `${BASE_URL}/api/v1/organizations/${orgId}`, null, readLatency);
    check(readRes, { 'org read': (r) => r.status === 200 });

    // Update
    const updateRes = request('PUT', `${BASE_URL}/api/v1/organizations/${orgId}`, {
      name: `Updated Org ${Date.now()}`,
    }, updateLatency);
    check(updateRes, { 'org updated': (r) => r.status === 200 });

    // List
    const listRes = request('GET', `${BASE_URL}/api/v1/organizations?page=0&size=20`, null, listLatency);
    check(listRes, { 'org list': (r) => r.status === 200 });

    // Search
    const searchRes = request('GET', `${BASE_URL}/api/v1/organizations/search?q=Perf`, null, searchLatency);
    check(searchRes, { 'org search': (r) => r.status === 200 });

    // Delete
    const deleteRes = request('DELETE', `${BASE_URL}/api/v1/organizations/${orgId}`, null, deleteLatency);
    check(deleteRes, { 'org deleted': (r) => r.status === 200 || r.status === 204 });
  });
}

// -------------------------------------------------------------------
// Scenario: Contact CRUD
// -------------------------------------------------------------------
function contactCrud() {
  group('Contact CRUD', () => {
    const createRes = request('POST', `${BASE_URL}/api/v1/contacts`, {
      firstName: 'Perf',
      lastName: `User${Date.now()}`,
      email: `perf-${Date.now()}@example.com`,
    }, createLatency);

    check(createRes, { 'contact created': (r) => r.status === 200 || r.status === 201 });

    const body = JSON.parse(createRes.body);
    const id = body.data ? body.data.id : body.id;

    if (!id) return;

    const readRes = request('GET', `${BASE_URL}/api/v1/contacts/${id}`, null, readLatency);
    check(readRes, { 'contact read': (r) => r.status === 200 });

    const updateRes = request('PUT', `${BASE_URL}/api/v1/contacts/${id}`, {
      firstName: 'UpdatedPerf',
    }, updateLatency);
    check(updateRes, { 'contact updated': (r) => r.status === 200 });

    const listRes = request('GET', `${BASE_URL}/api/v1/contacts?page=0&size=20`, null, listLatency);
    check(listRes, { 'contact list': (r) => r.status === 200 });

    const searchRes = request('GET', `${BASE_URL}/api/v1/contacts/search?q=Perf`, null, searchLatency);
    check(searchRes, { 'contact search': (r) => r.status === 200 });

    const deleteRes = request('DELETE', `${BASE_URL}/api/v1/contacts/${id}`, null, deleteLatency);
    check(deleteRes, { 'contact deleted': (r) => r.status === 200 || r.status === 204 });
  });
}

// -------------------------------------------------------------------
// Scenario: Lead CRUD
// -------------------------------------------------------------------
function leadCrud() {
  group('Lead CRUD', () => {
    const createRes = request('POST', `${BASE_URL}/api/v1/leads`, {
      title: `Perf Lead ${Date.now()}`,
      priority: 'MEDIUM',
      source: 'Website',
    }, createLatency);

    check(createRes, { 'lead created': (r) => r.status === 200 || r.status === 201 });

    const body = JSON.parse(createRes.body);
    const id = body.data ? body.data.id : body.id;

    if (!id) return;

    const readRes = request('GET', `${BASE_URL}/api/v1/leads/${id}`, null, readLatency);
    check(readRes, { 'lead read': (r) => r.status === 200 });

    const updateRes = request('PUT', `${BASE_URL}/api/v1/leads/${id}`, {
      title: `Updated Lead ${Date.now()}`,
    }, updateLatency);
    check(updateRes, { 'lead updated': (r) => r.status === 200 });

    const listRes = request('GET', `${BASE_URL}/api/v1/leads?page=0&size=20`, null, listLatency);
    check(listRes, { 'lead list': (r) => r.status === 200 });

    const searchRes = request('GET', `${BASE_URL}/api/v1/leads/search?q=Perf`, null, searchLatency);
    check(searchRes, { 'lead search': (r) => r.status === 200 });

    const statsRes = request('GET', `${BASE_URL}/api/v1/leads/stats`, null, readLatency);
    check(statsRes, { 'lead stats': (r) => r.status === 200 });

    const deleteRes = request('DELETE', `${BASE_URL}/api/v1/leads/${id}`, null, deleteLatency);
    check(deleteRes, { 'lead deleted': (r) => r.status === 200 || r.status === 204 });
  });
}

// -------------------------------------------------------------------
// Scenario: Deal CRUD
// -------------------------------------------------------------------
function dealCrud() {
  group('Deal CRUD', () => {
    const createRes = request('POST', `${BASE_URL}/api/v1/deals`, {
      name: `Perf Deal ${Date.now()}`,
      amount: 50000,
      currency: 'USD',
    }, createLatency);

    check(createRes, { 'deal created': (r) => r.status === 200 || r.status === 201 });

    const body = JSON.parse(createRes.body);
    const id = body.data ? body.data.id : body.id;

    if (!id) return;

    const readRes = request('GET', `${BASE_URL}/api/v1/deals/${id}`, null, readLatency);
    check(readRes, { 'deal read': (r) => r.status === 200 });

    const updateRes = request('PUT', `${BASE_URL}/api/v1/deals/${id}`, {
      name: `Updated Deal ${Date.now()}`,
    }, updateLatency);
    check(updateRes, { 'deal updated': (r) => r.status === 200 });

    const listRes = request('GET', `${BASE_URL}/api/v1/deals?page=0&size=20`, null, listLatency);
    check(listRes, { 'deal list': (r) => r.status === 200 });

    const searchRes = request('GET', `${BASE_URL}/api/v1/deals/search?q=Perf`, null, searchLatency);
    check(searchRes, { 'deal search': (r) => r.status === 200 });

    const pipelineRes = request('GET', `${BASE_URL}/api/v1/deals/pipeline`, null, readLatency);
    check(pipelineRes, { 'deal pipeline': (r) => r.status === 200 });

    const deleteRes = request('DELETE', `${BASE_URL}/api/v1/deals/${id}`, null, deleteLatency);
    check(deleteRes, { 'deal deleted': (r) => r.status === 200 || r.status === 204 });
  });
}

// -------------------------------------------------------------------
// Main test function
// -------------------------------------------------------------------
export default function () {
  organizationCrud();
  contactCrud();
  leadCrud();
  dealCrud();

  sleep(1); // 1s think time between iterations
}
