import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 50,
    duration: '120s',
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<5000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const EVENT_ID = __ENV.EVENT_ID || 'test-event-1';
const ZONE_ID = __ENV.ZONE_ID || 'zone-general';

const tokensFile = __ENV.TOKENS_FILE || '';
const tokens = tokensFile ? JSON.parse(open(tokensFile)) : [];
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

export default function () {
    const userToken = tokens.length > 0
        ? tokens[__VU % tokens.length]
        : AUTH_TOKEN;

    const joinRes = http.post(
        `${BASE_URL}/api/v1/queue/${EVENT_ID}/join`,
        null,
        { headers: { 'Authorization': `Bearer ${userToken}` } }
    );

    check(joinRes, { 'queue join ok': (r) => r.status === 200 });

    sleep(2);

    let tokenRes;
    for (let i = 0; i < 30; i++) {
        const statusRes = http.get(
            `${BASE_URL}/api/v1/queue/${EVENT_ID}/status`,
            { headers: { 'Authorization': `Bearer ${userToken}` } }
        );

        if (statusRes.status === 200 && statusRes.json().status === 'TURN_READY') {
            tokenRes = http.get(
                `${BASE_URL}/api/v1/queue/${EVENT_ID}/token`,
                { headers: { 'Authorization': `Bearer ${userToken}` } }
            );
            break;
        }
        sleep(2);
    }

    if (!tokenRes || tokenRes.status !== 200) {
        return;
    }

    const reservationPayload = JSON.stringify({
        eventId: EVENT_ID,
        items: [{ zoneId: ZONE_ID, quantity: 1 }],
    });

    const reservationRes = http.post(
        `${BASE_URL}/api/v1/reservations`,
        reservationPayload,
        {
            headers: {
                'Authorization': `Bearer ${userToken}`,
                'Content-Type': 'application/json',
            },
        }
    );

    check(reservationRes, { 'reservation ok': (r) => r.status === 201 });

    if (reservationRes.status !== 201) return;

    const reservationId = reservationRes.json().id;

    const paymentPayload = JSON.stringify({
        reservationId: reservationId,
        amount: 50.00,
    });

    const paymentRes = http.post(
        `${BASE_URL}/api/v1/payments`,
        paymentPayload,
        {
            headers: {
                'Authorization': `Bearer ${userToken}`,
                'Content-Type': 'application/json',
            },
        }
    );

    check(paymentRes, { 'payment initiated': (r) => r.status === 201 });

    if (paymentRes.status !== 201) return;

    const paymentId = paymentRes.json().id;

    const confirmPayload = JSON.stringify({
        idempotencyKey: `test-${__VU}-${__ITER}`,
    });

    const confirmRes = http.post(
        `${BASE_URL}/api/v1/payments/${paymentId}/confirm`,
        confirmPayload,
        {
            headers: {
                'Authorization': `Bearer ${userToken}`,
                'Content-Type': 'application/json',
            },
        }
    );

    check(confirmRes, { 'payment confirmed': (r) => r.status === 200 });
}
