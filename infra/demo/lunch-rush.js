// k6 load test: the 12:30 lunch rush. Students browse canteens and menus (public, read-only endpoints),
// which compute live wait estimates on every request. Pushes the backend's CPU so the HPA scales 2 -> 4 -> 6.
//
//   k6 run -e BASE_URL=http://smartcampus.localtest.me infra/demo/lunch-rush.js
//   (or: docker run --rm -i --network host -e BASE_URL=... grafana/k6:1.3.0 run - < infra/demo/lunch-rush.js)
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://smartcampus.localtest.me';

export const options = {
  stages: [
    { duration: '1m', target: 20 },   // 12:20 a few early students
    { duration: '2m', target: 120 },  // 12:30 classes end
    { duration: '4m', target: 250 },  // 12:35 peak: watch the HPA reach 4, then 6 pods
    { duration: '2m', target: 20 },   // 12:50 rush is over (scale-down waits 5 min by design)
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
  },
};

export default function () {
  const vendors = http.get(`${BASE_URL}/vendors`);
  check(vendors, { 'vendors 200': (r) => r.status === 200 });

  let ids = [];
  try {
    ids = vendors.json().map((v) => v.id);
  } catch (e) {
    // keep going; the check above records the failure
  }
  if (ids.length > 0) {
    const id = ids[Math.floor(Math.random() * ids.length)];
    const menu = http.get(`${BASE_URL}/vendors/${id}/menu`);
    check(menu, { 'menu 200': (r) => r.status === 200 });
  }
  sleep(Math.random() * 2 + 0.5);
}
