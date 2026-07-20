# Project Context — SSP/Exchange Simulator (read this first)

Briefs you (the terminal AI) on who I am, what I'm building, and how to work with me. **This is the inverse of my EKS-infra project: here AdTech and reactive Java are my strengths, not my blind spot.** Adjust accordingly.

## Who I am
- Senior Java backend engineer (~5 yrs) working on a real AdTech SSP/Exchange: Spring WebFlux/Netty, Reactor, Kafka, MongoDB, Kubernetes at large scale.
- **This is my domain.** SSP/DSP/Exchange mechanics, OpenRTB, the auction/bid lifecycle, and reactive Java are things I know cold.
- Treat me as a peer and sounding board, **not** a student. No tutorials on OpenRTB, the bid flow, Spring, Reactor, or Java. Skip the 101 and get to the point.

## What I'm building
- A **minimal SSP/Exchange simulator**, deployed on the EKS cluster I built by hand (separate infra repo).
- **Core:** one HTTP endpoint that receives an **OpenRTB** bid request and returns a response sourced from a DSP. Minimal exchange workflow — only what's needed to demonstrate the path end-to-end.
- Plus two convenience components for end-to-end testing:
  - a **supply-partner SSP** (traffic source), and
  - a **demand-partner DSP** (bidder).
- **One Kubernetes namespace per component** (e.g. `ssp`, `exchange`, `dsp`).
- **Per-component specifics (endpoints, payloads, exact workflow) I describe in the relevant channel.** Don't assume beyond what I've stated; ask if a detail is load-bearing and unspecified.

## Deployment target (the infra I already built)
- EKS cluster **`eks-dev`**, region **`eu-central-1`** (separate Terraform repo at `…/AWS_EKS/terraform`).
- Images → ECR: `140023370575.dkr.ecr.eu-central-1.amazonaws.com/eks-dev/app` (eu-central-1).
- Edge via the **Kubernetes Gateway API** (AWS LB Controller → ALB), an `HTTPRoute` per service.
- That cluster will also run LGTM observability and an Istio ambient mesh later — so build the services to be **trace/mesh-friendly** (emit OTLP where it's cheap to).

## How to work with me
1. **Assume domain expertise.** No AdTech/OpenRTB/Spring/Reactor explainers. Lead with the answer.
2. **Be concise.** Density over volume — tight bullets/tables, no filler, no recap paragraphs.
3. **Peer-review and push back.** If a design is wrong, latency-naive, or not how a real exchange behaves, say so plainly. Honest peer beats yes-man.
4. **Simulator-grade, not prod.** Simplest thing that demonstrates the workflow. Flag "prod would do X; here Y is fine."
5. **Code ownership:** generate boilerplate/scaffolding freely (Spring skeleton, Dockerfile, k8s manifests); for domain/core logic, propose and let me decide. **I run builds/tests/deploys myself** — hand me the commands.

## Conventions / stack (confirm per component; some TBD)
- Java + Spring Boot; reactive stack (WebFlux/Netty) likely, given my background — confirm per service.
- OpenRTB version: TBD (I'll specify).
- Containerized (Docker) → ECR → EKS via Gateway API `HTTPRoute`, namespace-per-component.
- **Synthetic data only.** All bid requests/responses are simulated — no real customer PII, no production revenue data. (Matches org data-handling rules.)

## Sequencing
- Start with the Exchange's single endpoint (OpenRTB in → DSP response out), then the dummy DSP, then the supply SSP; wire them end-to-end across namespaces.
- Deploy early and often to the real cluster — the point is the whole system (app + platform) running together.
