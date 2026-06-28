import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 10000,
    duration: '30s',
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<2000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const EVENT_ID = __ENV.EVENT_ID || 'test-event-1';

const tokensFile = __ENV.TOKENS_FILE || '';
const tokens = tokensFile ? JSON.parse(open(tokensFile)) : [];
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

export default function () {
    const token = tokens.length > 0
        ? tokens[__VU % tokens.length]
        : AUTH_TOKEN;
    const res = http.post(
        `${BASE_URL}/api/v1/queue/${EVENT_ID}/join`,
        null,
        {
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
            },
        }
    );

    check(res, {
        'status is 200': (r) => r.status === 200,
        'has ticketId': (r) => {
            const body = r.json();
            return body && body.ticketId !== undefined;
        },
        'has position': (r) => {
            const body = r.json();
            return body && body.position > 0;
        },
    });

    sleep(0.1);
}
