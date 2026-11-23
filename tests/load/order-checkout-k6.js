import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:18080';

export const options = {
  stages: [
    { duration: '10s', target: 5 },
    { duration: '20s', target: 10 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

export function setup() {
  const payload = JSON.stringify({
    name: 'K6 Load Test Product',
    price: 150000,
    stock: 100000,
  });
  const res = http.post(`${BASE_URL}/api/v1/products`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, {
    'setup product created (201)': (r) => r.status === 201,
  });
  return { productId: res.json('id') };
}

export default function (data) {
  const jsonHeaders = { headers: { 'Content-Type': 'application/json' } };

  const productsRes = http.get(`${BASE_URL}/api/v1/products`);
  check(productsRes, {
    'GET /api/v1/products status 200': (r) => r.status === 200,
  });

  const cartRes = http.post(
    `${BASE_URL}/api/v1/carts`,
    JSON.stringify({ customerId: 101 }),
    jsonHeaders
  );
  check(cartRes, {
    'POST /api/v1/carts status 201': (r) => r.status === 201,
  });

  const cartId = cartRes.json('id');
  if (cartId && data.productId) {
    const itemRes = http.post(
      `${BASE_URL}/api/v1/carts/${cartId}/items`,
      JSON.stringify({ productId: data.productId, quantity: 1 }),
      jsonHeaders
    );
    check(itemRes, {
      'POST /api/v1/carts/{id}/items status 201': (r) => r.status === 201,
    });

    const orderRes = http.post(
      `${BASE_URL}/api/v1/orders`,
      JSON.stringify({ cartId: cartId, customerId: 101 }),
      jsonHeaders
    );
    check(orderRes, {
      'POST /api/v1/orders status 201': (r) => r.status === 201,
    });
  }

  sleep(0.5);
}
