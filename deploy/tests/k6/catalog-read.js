import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 5000,
    duration: '30s',
    thresholds: {
        http_req_duration: ['p(95)<200'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';

const EVENTS_QUERY = JSON.stringify({
    query: `{
        events(page: 0, size: 20) {
            content {
                id
                name
                type
                date
                venue { name }
                zones { name capacity price }
            }
            totalElements
        }
    }`,
});

const AVAILABILITY_QUERY = JSON.stringify({
    query: `{
        availability(eventId: "test-event-1") {
            zoneId
            zoneName
            totalCapacity
            availableCount
        }
    }`,
});

export default function () {
    const eventsRes = http.post(
        `${BASE_URL}/graphql`,
        EVENTS_QUERY,
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(eventsRes, {
        'events query ok': (r) => r.status === 200,
        'events has data': (r) => {
            const body = r.json();
            return body && body.data && body.data.events;
        },
    });

    sleep(0.1);

    const availRes = http.post(
        `${BASE_URL}/graphql`,
        AVAILABILITY_QUERY,
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(availRes, {
        'availability query ok': (r) => r.status === 200,
    });

    sleep(0.1);
}
