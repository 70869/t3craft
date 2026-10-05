# Runtime protocol

T3Craft connects directly from Java to paired T3 Code environments. The former Node Foreman bridge is removed.

See [T3 integration](T3-INTEGRATION.md) for the HTTP routes, auth, protocol negotiation, ticketed WebSocket subscriptions and feature coverage.

The Java `ForemanSnapshot` and related studio types remain an internal compatibility model for HQ rendering. `T3Projection` derives those views from actual T3 state; they are not a public orchestration API or a second chat store.

The opt-in local DevBridge protocol is developer tooling documented in [mod/DEV.md](../mod/DEV.md). It is disabled in the installed Prism profile.

