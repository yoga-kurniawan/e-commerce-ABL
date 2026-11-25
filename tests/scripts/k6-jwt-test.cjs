// Validate load-test role selection and refresh decisions without running a load generator.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../load/order-checkout-k6.js'), 'utf8')
  .replace(/^import .*;\n/gm, '').replace('export const options', 'const options')
  .replace('export function setup', 'function setup').replace('export default function', 'function iteration');
function run(env = {}) {
  const clock = { seconds: 1800000000 };
  const state = { logins: [], requests: [], errors: 0 };
  function token(role, customerId = '101', exp = clock.seconds + 120) {
    return 'fixture.' + Buffer.from(JSON.stringify({ exp, customer_id: customerId, realm_access: { roles: [role] } })).toString('base64url') + '.unsigned';
  }
  function claims(value) { return JSON.parse(Buffer.from(value.split('.')[1], 'base64url').toString()); }
  function response(status, body) { return { status, json: key => key ? body[key] : body }; }
  const http = {
    get(url, params) {
      assert.equal(claims(params.headers.Authorization.slice(7)).realm_access.roles[0], 'CUSTOMER');
      state.requests.push({ url }); return response(200, []);
    },
    post(url, body, params) {
      if (typeof body === 'object') {
        state.logins.push(body.username);
        return response(200, { access_token: token(body.username === 'api-admin' ? 'ADMIN' : 'CUSTOMER') });
      }
      const parsed = JSON.parse(body); const role = claims(params.headers.Authorization.slice(7)).realm_access.roles[0];
      assert.equal(role, url.endsWith('/products') ? 'ADMIN' : 'CUSTOMER');
      if (url.endsWith('/carts') || url.endsWith('/orders')) assert.equal(parsed.customerId, 101);
      state.requests.push({ url, body: parsed }); return response(201, { id: state.requests.length });
    },
  };
  class FakeDate extends Date { static now() { return clock.seconds * 1000; } }
  class Counter { add(n) { state.errors += n; } }
  const context = vm.createContext({ __ENV: { K6_ADMIN_PASSWORD: 'fixture', K6_CUSTOMER_PASSWORD: 'fixture', ...env }, http,
    encoding: { b64decode: s => Buffer.from(s, 'base64url').toString() }, check: () => true,
    fail: message => { throw new Error(message); }, sleep: () => {}, Counter, Date: FakeDate });
  const api = vm.runInContext(source + '\n({setup, iteration, getToken, options});', context);
  return { api, clock, state, token };
}
let r = run(); const data = r.api.setup(); r.api.iteration(data);
assert.equal(r.state.logins.filter(x => x === 'customer-a').length, 1);
r.clock.seconds += 61; r.api.iteration(data);
assert.equal(r.state.logins.filter(x => x === 'customer-a').length, 2);
assert.equal(r.state.logins.filter(x => x === 'api-admin').length, 1);
assert.equal(r.state.errors, 0);
assert.equal(r.api.options.thresholds.jwt_auth_failures[0], 'count==0');
console.log('PASS k6 separates admin/customer headers and refreshes customer login');
const token = r.token('CUSTOMER', '101', 1800000120);
r = run({ CUSTOMER_TOKEN: token }); r.api.getToken('customer'); r.clock.seconds += 61;
assert.throws(() => r.api.getToken('customer'), /expires soon/); assert.equal(r.state.errors, 1);
assert.equal(r.state.logins.length, 0);
console.log('PASS k6 fails explicitly when an external token expires');
r = run({ CUSTOMER_ID: '202' }); assert.throws(() => r.api.getToken('customer'), /CUSTOMER_ID/);
assert.equal(r.state.errors, 1);
console.log('PASS k6 rejects fixture customer mismatches');
r = run({ CUSTOMER_TOKEN: r.token('ADMIN') }); assert.throws(() => r.api.getToken('customer'), /Wrong role/);
console.log('PASS k6 refuses admin tokens for customer checkout');

r = run({ CUSTOMER_TOKEN: 'fixture.' + Buffer.from('null').toString('base64url') + '.unsigned' });
assert.throws(() => r.api.getToken('customer'), /Invalid customer token claims/);
assert.equal(r.state.errors, 1);
console.log('PASS k6 counts malformed claim payloads as authentication failures');
