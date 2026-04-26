// CinéTrack baseline load test — covered in chapter 5.
// Run with: k6 run --out experimental-prometheus-rw=http://prom:9090/api/v1/write baseline.js
//
// Traffic shape: 30s ramp from 0 to 50 VUs, 2m steady at 50, 30s ramp down.
// The thresholds align with chapter 1's latency budget — p99 under 500ms,
// error rate under 1%. Failing thresholds turn the run red in CI.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const watchlogLatency = new Trend('watchlog_create_latency', true);

export const options = {
    scenarios: {
        baseline: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '2m', target: 50 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<500'],
        http_req_failed: ['rate<0.01'],
        'watchlog_create_latency': ['p(95)<300'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Each VU registers once at the start and reuses the JWT for the whole run.
export function setup() {
    const tokens = [];
    for (let i = 0; i < 50; i++) {
        const username = `loadtest_u${i}_${Date.now()}`;
        const email = `${username}@example.com`;
        const res = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
            username, email, password: 'Password1!'
        }), { headers: { 'Content-Type': 'application/json' } });

        if (res.status === 201) {
            tokens.push(res.json().token);
        }
    }
    return { tokens };
}

export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];
    const headers = {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
    };

    // 80% reads, 20% writes — matches CinéTrack's measured production mix.
    if (Math.random() < 0.8) {
        const res = http.get(`${BASE_URL}/api/watchlogs`, { headers });
        check(res, { 'list returns 200': (r) => r.status === 200 });
    } else {
        const tmdbId = 9000 + Math.floor(Math.random() * 1000);
        const start = Date.now();
        const res = http.post(`${BASE_URL}/api/watchlogs`, JSON.stringify({
            tmdbId,
            movieTitle: `LoadTest Movie ${tmdbId}`,
            watchedDate: '2024-01-01',
            rating: 4,
            notes: 'k6 baseline run',
        }), { headers });
        watchlogLatency.add(Date.now() - start);
        check(res, { 'create returns 201 or 409': (r) => r.status === 201 || r.status === 409 });
    }

    // Pacing — 1 request/sec/VU keeps total throughput at ~50 RPS, leaving the
    // server room to expose its baseline latency rather than its saturation point.
    sleep(1);
}
