# Maintain.ai Android Client Contract

This client integrates with the Maintain.ai platform architecture.

## Authority

The Maintain.ai backend is authoritative for:
- authenticated user identity
- organization membership
- machine visibility
- role permissions
- work-order permissions
- telemetry access
- ML/analytics access

The Android client must never infer organization membership from a local value or use a hard-coded organization ID.

## Application context

Authenticated API requests use:

`Authorization: Bearer <Supabase access token>`

and:

`X-Maintain-Application: android`

The backend remains responsible for validating both authentication and application access.

## Authentication flow

1. Authenticate with Supabase using the publishable key.
2. Store the returned access token in Android app storage.
3. Call `POST /api/auth/supabase/sync` against the Maintain.ai backend.
4. Call `GET /api/auth/me`.
5. Use the returned organization and role information only as display/session context.
6. Let backend endpoints enforce actual permissions.

## API base URL

The API base URL is configured with the build environment variable:

`MAINTAIN_API_URL`

It defaults to the current Maintain.ai deployment for development compatibility. Production deployments should provide the canonical API hostname through the build environment.

## Realtime

Realtime authorization is obtained from:

`POST /api/auth/realtime-token`

The client then joins the organization telemetry topic using the backend-issued token. Client-side filtering is not a security boundary.

## Machine scope

Android may display machines returned by the backend, but it must not assume that a machine ID is accessible merely because it was supplied by navigation or local state.

## ML semantics

Analytics shown by Android must preserve backend semantics. Health-score prediction is not automatically a calibrated probability of failure. Model status/version should be displayed when relevant.

## Do not add

Do not add hard-coded organization IDs, trusted telemetry writes, client-side authorization, worker-only credentials, or a second authorization system.
