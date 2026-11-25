import http from 'k6/http';
import encoding from 'k6/encoding';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const AUTH_URL = __ENV.AUTH_URL || 'http://localhost:18090/realms/ecommerce/protocol/openid-connect/token';
const authFailures = new Counter('jwt_auth_failures');
const cache = {};

export const options = {
  stages: [
    { duration: '10s', target: 5 },
    { duration: '20s', target: 10 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
    jwt_auth_failures: ['count==0'],
  },
};

function authError(message) {
  authFailures.add(1);
  fail(message); // Never print credentials, tokens or login response bodies.
}

function getToken(role) {
  const now = Date.now() / 1000;
  if (cache[role] && cache[role].exp > now + 60) return cache[role];
  const external = role === 'admin' ? __ENV.ADMIN_TOKEN : __ENV.CUSTOMER_TOKEN;
  const username = role === 'admin' ? (__ENV.K6_ADMIN_USER || 'api-admin') : (__ENV.K6_CUSTOMER_USER || 'customer-a');
  const password = role === 'admin' ? __ENV.K6_ADMIN_PASSWORD : __ENV.K6_CUSTOMER_PASSWORD;
  let token = external;
  // External tokens must be renewed by the caller. Credential mode refreshes per VU.
  if (!external) {
    if (!password) authError(`Supply ${role} token or K6_${role.toUpperCase()}_PASSWORD before running load test`);
    const res = http.post(AUTH_URL, {
      grant_type: 'password', client_id: __ENV.AUTH_CLIENT_ID || 'ecommerce-cli', username, password,
    }, { tags: { name: 'JWT login' } });
    if (res.status !== 200) authError(`JWT ${role} login failed (HTTP ${res.status})`);
    try { token = res.json('access_token'); } catch (_) { authError('Invalid login response'); }
  }
  let claims;
  try { claims = JSON.parse(encoding.b64decode(token.split('.')[1], 'rawurl', 's')); }
  catch (_) { authError(`Invalid ${role} token metadata`); }
  if (!claims || typeof claims !== 'object' || Array.isArray(claims)) authError(`Invalid ${role} token claims`);
  const roles = claims.realm_access && claims.realm_access.roles;
  if (!Number.isFinite(claims.exp) || claims.exp <= now + 60) authError(`${role} token expired or expires soon; renew it`);
  if (!Array.isArray(roles) || !roles.includes(role === 'admin' ? 'ADMIN' : 'CUSTOMER') || (role === 'customer' && roles.includes('ADMIN'))) {
    authError(`Wrong role for ${role} token`);
  }
  const customerId = Number(claims.customer_id);
  if (role === 'customer' && (!Number.isSafeInteger(customerId) || customerId <= 0 || (__ENV.CUSTOMER_ID && Number(__ENV.CUSTOMER_ID) !== customerId))) {
    authError('CUSTOMER_ID must match the customer token and be a positive safe integer');
  }
  cache[role] = { token, exp: claims.exp, customerId };
  return cache[role];
}

function headers(token) {
  return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } };
}

export function setup() {
  authFailures.add(0);
  const admin = getToken('admin');
  const customer = getToken('customer');
  // Verify server-side JWT acceptance before creating fixtures. Decoding above is metadata only.
  const preflight = http.get(`${BASE_URL}/api/v1/products`, headers(customer.token));
  if (preflight.status !== 200) authError(`JWT preflight failed (HTTP ${preflight.status})`);
  const productIds = [];
  for (let i = 1; i <= 3; i++) {
    const res = http.post(`${BASE_URL}/api/v1/products`, JSON.stringify({
      name: `K6 Load Test Product ${i}`, price: 150000, stock: 100000,
    }), headers(admin.token));
    check(res, { 'setup product created (201)': r => r.status === 201 });
    if (res.status !== 201) throw new Error(`Fixture Product failed (HTTP ${res.status})`);
    productIds.push(res.json('id'));
  }
  return { productIds, customerId: customer.customerId };
}

export default function (data) {
  const customer = getToken('customer');
  if (customer.customerId !== data.customerId) authError('Customer identity changed during the test');
  const jsonHeaders = headers(customer.token);
  const productsRes = http.get(`${BASE_URL}/api/v1/products`, jsonHeaders);
  check(productsRes, { 'GET /api/v1/products status 200': r => r.status === 200 });
  const cartRes = http.post(`${BASE_URL}/api/v1/carts`, JSON.stringify({ customerId: data.customerId }), jsonHeaders);
  check(cartRes, { 'POST /api/v1/carts status 201': r => r.status === 201 });
  if (cartRes.status !== 201) return;
  const cartId = cartRes.json('id');
  for (const productId of data.productIds) {
    const itemRes = http.post(`${BASE_URL}/api/v1/carts/${cartId}/items`, JSON.stringify({ productId, quantity: 1 }), jsonHeaders);
    check(itemRes, { 'POST /api/v1/carts/{id}/items status 201': r => r.status === 201 });
  }
  const orderRes = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify({ cartId, customerId: data.customerId }), jsonHeaders);
  check(orderRes, { 'POST /api/v1/orders status 201': r => r.status === 201 });
  sleep(0.5);
}
