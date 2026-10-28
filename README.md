# tn-service

TN Service provides useful service level utility classes and functions. 

## Health

A service that depends on tn-service is given, without configuring anything:

- `GET /actuator/health/liveness`: the process is running. Nothing outside the process is asked.
- `GET /actuator/health/readiness`: the service can do its own work, that is, it has started and, if it has a datasource, the datasource is reachable.
  It answers 503 until then. It never depends on another service: a service whose dependency is down, or whose circuit breaker is open, is still ready.
- `GET /actuator/resilience`: the state of the protection of each service called (see tn-resilience); present where tn-resilience is.
- `GET /noop` is unchanged (`tn.service.noop.enabled`).

Only `health` and `resilience` are exposed, and health shows status, never detail. What a service exposes itself is merged with these, not replaced
(`management.endpoints.web.exposure.include: env` gives `env,health,resilience`; `*` is left alone). Every other setting here is a default of the lowest
priority, so a service's own `management.endpoint.health.*` wins. The defaults are set by `HealthEnvironmentPostProcessor`.
