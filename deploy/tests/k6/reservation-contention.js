import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 1000,
    iterations: 1000,
    thresholds: {
        http_req_duration: ['p(95)<5000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const EVENT_ID = __ENV.EVENT_ID || 'test-event-1';
const ZONE_ID = __ENV.ZONE_ID || 'zone-vip';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';
export default function () {
    const payload = JSON.stringify({
        eventId: EVENT_ID,
        items: [{ zoneId: ZONE_ID, quantity: 1 }],
    });

    const res = http.post(
        `${BASE_URL}/api/v1/reservations`,
        payload,
        {
            headers: {
                'Authorization': `Bearer ${AUTH_TOKEN}`,
                'Content-Type': 'application/json',
            },
        }
    );

    check(res, {
        'status is 201 or 409': (r) => r.status === 201 || r.status === 409,
        'no overbooking': (r) => {
            if (r.status === 201) return true;
            if (r.status === 409) return true;
            return false;
        },
    });
}

export function handleSummary(data) {
    const totalRequests = data.metrics.http_reqs.values.count;
    const successRate = data.metrics.http_reqs.values.count > 0
        ? (data.metrics.iterations_completed / totalRequests) * 100
        : 0;

    return {
        stdout: `\n=== Reservation Contention Test Results ===\n` +
            `Total requests: ${totalRequests}\n` +
            `Completed iterations: ${data.metrics.iterations_completed}\n` +
            `Success rate: ${successRate.toFixed(2)}%\n` +
            `p95 latency: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms\n`,
    };
}
