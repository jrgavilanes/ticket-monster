## ADDED Requirements

### Requirement: List events
The system SHALL return a paginated list of available events with venue, artist, date, and availability information via GraphQL.

#### Scenario: Query all events
- **WHEN** a client sends a GraphQL `events` query with pagination parameters (offset, limit)
- **THEN** the system SHALL return a paginated list of events with id, name, venue, date, artist, and available ticket count

#### Scenario: No events available
- **WHEN** a client sends a GraphQL `events` query and no events exist
- **THEN** the system SHALL return an empty list with total count of 0

### Requirement: View event details
The system SHALL return detailed information for a specific event including venue layout, zone availability, pricing, and artist information.

#### Scenario: Query existing event
- **WHEN** a client sends a GraphQL `event(id)` query with a valid event ID
- **THEN** the system SHALL return the event's full details including venue, zones, pricing tiers, artist info, and real-time availability per zone

#### Scenario: Query non-existent event
- **WHEN** a client sends a GraphQL `event(id)` query with an invalid event ID
- **THEN** the system SHALL return a null result with an appropriate error message

### Requirement: Search events
The system SHALL support searching events by name, artist, venue, date range, and event type via GraphQL.

#### Scenario: Search by artist name
- **WHEN** a client sends a `searchEvents(query)` with an artist name filter
- **THEN** the system SHALL return all events matching the artist name, sorted by date ascending

#### Scenario: Search with multiple filters
- **WHEN** a client sends a `searchEvents(query)` with venue, date range, and event type filters combined
- **THEN** the system SHALL return events matching ALL provided filters

#### Scenario: Search with no results
- **WHEN** a client sends a `searchEvents(query)` with criteria matching no events
- **THEN** the system SHALL return an empty list

### Requirement: Check ticket availability
The system SHALL provide real-time ticket availability per zone/section for a given event.

#### Scenario: Query availability for event with stock
- **WHEN** a client sends an `availability(eventId)` query
- **THEN** the system SHALL return each zone/section with total capacity, reserved count, sold count, and available count

#### Scenario: Query availability for sold-out event
- **WHEN** a client sends an `availability(eventId)` query for a fully reserved/sold event
- **THEN** the system SHALL return all zones with available count of 0

### Requirement: Manage catalog entities (Admin)
The system SHALL allow ADMIN users to create, update, and delete venues, events, artists, event dates, and zones.

#### Scenario: Create new event
- **WHEN** an ADMIN user submits a create event mutation with valid venue, artist, date, and zone configuration
- **THEN** the system SHALL persist the event in MongoDB and return the created event with generated ID

#### Scenario: Unauthorized catalog modification
- **WHEN** a USER role (non-ADMIN) attempts to create, update, or delete a catalog entity
- **THEN** the system SHALL reject the request with HTTP 403 Forbidden

#### Scenario: Update event details
- **WHEN** an ADMIN user submits an update event mutation with modified fields
- **THEN** the system SHALL update the event in MongoDB and return the updated event
