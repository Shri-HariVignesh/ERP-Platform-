# Platform architecture

## Module boundary

A module owns a vertical slice: its domain tables, its workflows, its views. Modules do not
import each other's internals. Where two modules must meet, the meeting is a declared contract
in this directory — never a shared repository class and never a foreign key across slices.

A module is expected to carry, at minimum:

* `README.md` — what it is, how to run it, what is deliberately not built
* `docs/STATE_CONTRACT.md` — every state, actor, event, guard and side effect it can produce
* its own build file and its own tests

## Tenancy

`tenantId` is a required column on every domain table and a required argument on every query.
The enforcement is structural, not conventional: repositories expose only functions that carry
the scope, so an unscoped read is impossible to call by accident rather than a code-review
catch. Exceptions exist but must be enumerated and documented — see
[`../modules/student-experience/docs/REPOSITORY_SCOPE_RULES.md`](../modules/student-experience/docs/REPOSITORY_SCOPE_RULES.md)
for the pattern and the justified exceptions in that module.

## State changes

Every state change goes through one guard function that resolves `(type, state, event, actor)`
against a declared transition matrix and throws on anything unmatched. Side effects are declared
on the edge, not scattered through service code, and fire in the same transaction as the state
write. Automation is modelled as an actor (`SYSTEM`), so an automated approval is an ordinary
row in the same audit trail as a human one.

This is what makes the audit trail complete by construction: there is no second code path that
can move a record.

## Read model

The UI reads a normalized card shape, not the domain entity. Type-specific branching happens
once, where the card is built; the view layer renders badges, timelines and action buttons from
declared fields. A new workflow type adds a matrix entry and a payload DTO — not a controller,
not a template, not a schema migration.

## Cross-module concerns — not yet decided

Authentication, notification delivery, file storage and payments are stubbed inside
`student-experience` and will need a platform answer before a second module lands. They are
listed here so the debt is visible, not because a design exists.
