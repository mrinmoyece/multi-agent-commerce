# Limitations

- Inventory data is deterministic and synthetic.
- Pricing and sentiment findings are model-generated narratives without live
  market, review, support, or returns feeds. They are not decision-grade.
- There is no HTTP API, authentication layer, database, briefing history, or
  append-only audit log.
- Scheduling and results are process-local. Horizontal replicas would duplicate
  scheduled work, so the supported Kubernetes topology is one replica.
- Provider calls are bounded but cannot make a nondeterministic model fully
  reliable or factual.
- The network policy allows public HTTPS because provider endpoints vary. A
  production platform should narrow egress to organization-approved proxies or
  provider ranges.
- Kubernetes secrets are references, not a complete secret-management system.
  Production deployments should use an external secret manager and workload
  identity where supported.
