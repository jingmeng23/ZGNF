// k6 run -e BASE_URL=http://127.0.0.1:8080 -e PRODUCT_IDS=1,3 -e RATE=100 scripts/load/product-detail.js
// Run only against an authorized test environment with prepared, publicly visible products.
import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const ids = (__ENV.PRODUCT_IDS || '').split(',').filter(Boolean);
if (!ids.length) throw new Error('PRODUCT_IDS must contain real, public product IDs');
const base = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const rejected = new Rate('detail_rejected');
export const options = {
  scenarios: {
    detail: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 100), timeUnit: '1s', duration: __ENV.DURATION || '3m',
      preAllocatedVUs: Number(__ENV.VUS || 50), maxVUs: Number(__ENV.MAX_VUS || 200),
    },
  },
  // Candidate acceptance targets, not measured results or universal SLOs.
  thresholds: { http_req_duration: ['p(95)<300', 'p(99)<500'], checks: ['rate>0.99'], detail_rejected: ['rate<0.01'], dropped_iterations: ['count==0'] },
};
export default function () {
  // 80% of requests target a single hot key; the rest exercise the configured long tail.
  const id = __ENV.UNIFORM === 'true' || Math.random() >= 0.8 ? ids[Math.floor(Math.random() * ids.length)] : ids[0];
  const response = http.get(`${base}/api/v1/mall/products/${encodeURIComponent(id)}`, { tags: { name: 'GET mall product detail' }, timeout: '5s' });
  rejected.add(response.status === 503);
  check(response, { 'detail response is complete': (r) => {
    if (r.status !== 200) return false;
    try { const body = r.json(); return body.code === 200 && String(body.data.product.id) === id && Array.isArray(body.data.mainImages) && body.data.matrix != null; }
    catch (_) { return false; }
  } });
}
