## ADDED Requirements

### Requirement: List events
The system SHALL expose a GraphQL query `events` that returns a paginated list of all published events with their venue, date, type, and available zones.

#### Scenario: Query all events
- **WHEN** a client sends the `events` GraphQL query with pagination parameters (page, size)
- **THEN** the system SHALL return a paginated list of published events including venue name, event date, event type, and zone availability summary

#### Scenario: No events available
- **WHEN** a client sends the `events` query and no published events exist
- **THEN** the system SHALL return an empty list with total count of zero

### Requirement: Get event details
The system SHALL expose a GraphQL query `event(id)` that returns full details of a single event including venue layout, all zones with pricing and availability, artist lineup, and event metadata.

#### Scenario: Query existing event
- **WHEN** a client sends `event(id: "abc123")` for a valid event ID
- **THEN** the system SHALL return the complete event object with venue, zones (name, capacity, price, available count), artists, and date

#### Scenario: Query non-existent event
- **WHEN** a client sends `event(id: "nonexistent")` for an ID that does not exist
- **THEN** the system SHALL return null and the GraphQL response SHALL NOT contain an error (standard GraphQL null behavior for missing entities)

### Requirement: Search events
The system SHALL expose a GraphQL query `searchEvents(query)` that supports full-text search by event name, artist name, venue name, date range, and event type.

#### Scenario: Search by artist name
- **WHEN** a client sends `searchEvents(query: "Bad Bunny")`
- **THEN** the system SHALL return all events where the artist name matches the query, sorted by date ascending

#### Scenario: Search with filters
- **WHEN** a client sends `searchEvents` with a query string and filter arguments (type, dateFrom, dateTo, venueId)
- **THEN** the system SHALL return events matching the text query AND all provided filters

#### Scenario: Search with no results
- **WHEN** a client sends a search query that matches no events
- **THEN** the system SHALL return an empty list

### Requirement: Query zone availability
The system SHALL expose a GraphQL query `availability(eventId)` that returns real-time seat/zone availability for a given event, sourced from the Reservation Module's PostgreSQL store (not MongoDB).

#### Scenario: Check availability for event with stock
- **WHEN** a client queries `availability(eventId: "abc123")` for an event with available tickets
- **THEN** the system SHALL return each zone with total capacity, reserved count, and available count

#### Scenario: Check availability for sold-out event
- **WHEN** a client queries `availability(eventId: "soldout")` for an event with zero available tickets
- **THEN** the system SHALL return all zones with available count of zero

### Requirement: Manage catalog entities (admin)
The system SHALL allow users with the `ADMIN` role to create, update, and delete venues, events, artists, and zones via GraphQL mutations.

#### Scenario: Admin creates event
- **WHEN** an authenticated user with `ADMIN` role sends a `createEvent` mutation with valid event data
- **THEN** the system SHALL persist the event in MongoDB and return the created event object

#### Scenario: Non-admin attempts to create event
- **WHEN** an authenticated user without `ADMIN` role sends a `createEvent` mutation
- **THEN** the system SHALL reject the request with a 403 Forbidden error

### Requirement: Catalog data stored in MongoDB
The system SHALL persist all catalog entities (venues, events, artists, zones) in MongoDB to leverage flexible document schemas for diverse event types.

#### Scenario: Store concert event with artist lineup
- **WHEN** an admin creates a concert event with multiple artists, genres, and a stage layout
- **THEN** the system SHALL store the event as a MongoDB document with nested artist and zone arrays

#### Scenario: Store sports event with different schema
- **WHEN** an admin creates a sports event with teams, league, and match format
- **THEN** the system SHALL store the event as a MongoDB document with sports-specific fields without requiring schema migration
