import http from 'k6/http';
import { check } from 'k6';

export const options = {
    thresholds: {
        http_req_failed: ['rate<1.0'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const QUERY = JSON.stringify({
    query: `{ events(page: 0, size: 1) { totalElements } }`,
});

export default function () {
    const res = http.post(`${BASE_URL}/graphql`, QUERY, {
        headers: { 'Content-Type': 'application/json' },
    });
    check(null, {
        'status 200': () => res.status === 200,
        'status 405': () => res.status === 405,
        'status 429': () => res.status === 429,
        'status 503': () => res.status === 503,
        'status other': () => ![200, 405, 429, 503].includes(res.status),
    });
}
