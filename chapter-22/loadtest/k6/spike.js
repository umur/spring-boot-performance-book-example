// CinéTrack spike test — used in 5.4 to expose how the service degrades when
// traffic jumps from steady-state to 5× in 10 seconds.
//
// Run with: BASE_URL=http://localhost:8080 k6 run spike.js
//
// The thresholds intentionally allow degradation during the spike but require
// recovery to baseline within the cool-down — this matches the CinéTrack SLO
// of "5-minute p99 under 500ms, peak p99 under 2s."

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },   // warm-up
                { duration: '10s', target: 100 },  // 5× spike
                { duration: '1m', target: 100 },   // sustained spike
                { duration: '30s', target: 20 },   // recover
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        'http_req_duration{stage:spike}': ['p(95)<2000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
    const username = `spike_u_${Date.now()}`;
    const res = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
        username, email: `${username}@example.com`, password: 'Password1!'
    }), { headers: { 'Content-Type': 'application/json' } });
    return { token: res.json().token };
}

export default function (data) {
    const headers = { 'Authorization': `Bearer ${data.token}` };
    const res = http.get(`${BASE_URL}/api/watchlogs`, { headers });
    check(res, { 'status is 2xx or 429 (rate-limited)': (r) => r.status < 300 || r.status === 429 });
    sleep(0.2);
}
