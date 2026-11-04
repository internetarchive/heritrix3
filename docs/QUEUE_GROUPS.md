# Queue Groups

Queue groups let you treat several otherwise-independent per-host work queues as
if they were a **single web server** for the purposes of politeness and
scheduling.

The URLs are **not** merged into a single queue: each host keeps its own
`WorkQueue` (its own `classKey`). A group only adds, on top of the existing flat
frontier scheduler:

- a **shared politeness gate**: at most `maxParallelInGroup` member queues may be
  "in process" simultaneously, and a shared cool-down of at least
  `groupMinDelayMs` milliseconds is enforced between fetches of the group;
- a **round-robin rotation** among the group's member queues, so the crawler
  alternates between hosts (`european-union.europa.eu`, then
  `commission.europa.eu`, ...) instead of draining one host first. Members that
  are not currently ready (snoozed, empty) are simply skipped.

With `maxParallelInGroup=1` and a shared cool-down, the whole group behaves as a
single scheduling unit toward the server: its member hosts share a single fetch
slot.

Queue groups are **optional**. When the `queueGroups` bean is absent (as by
default), the frontier behaves exactly as before.

## How the queueing system works

This section explains the machinery the `WorkQueueFrontier` uses to schedule
work, and how a queue group hooks into it. Understanding the queue *states* is
key to understanding what a group actually changes.

### Per-host work queues

Every URI is assigned a `classKey` by the configured `queueAssignmentPolicy`
(see below), and all URIs sharing a `classKey` land in the same `WorkQueue`.
With the default policy there is essentially one queue per host authority. The
frontier never merges these queues; a queue group only coordinates *when* each
member queue is allowed to run.

### Queue states

At any moment a `WorkQueue` is in exactly one of the following states, and the
frontier moves queues between them:

- **inactive** — the queue holds URIs but is not currently competing for a
  fetch slot. Inactive queues are ordered by **precedence**; the highest
  precedence ones are promoted (`activateInactiveQueue()`) into the ready set.
- **ready** — the queue is eligible to be fetched next. Ready queues live in
  `readyClassQueues`.
- **in-process** — one of the queue's URIs is currently being fetched. A queue
  is placed in `inProcessQueues` while its URI is out with a processor thread.
- **snoozed** — the queue is waiting for a **real, future wake time** (its
  per-host politeness delay, a retry delay, or a group cool-down). Snoozed
  queues sit in `snoozedClassQueues`, ordered by wake time, and `wakeQueues()`
  returns them to *ready* once their time has come.
- **parked (group waiting list)** — a group member that is ready in itself but
  is held back **only** because the group's concurrency ceiling
  (`maxParallelInGroup`) is currently full. Parked queues are neither ready nor
  snoozed; they wait on their `QueueGroup`'s waiting list until a sibling
  releases a slot (see below).
- **retired** — the queue is out of the crawl (e.g. budget exhausted).

### The main scheduling loop

`findEligibleURI()` runs on the frontier's manager thread and repeatedly:

1. pulls the next **ready** queue from `readyClassQueues`;
2. if the queue belongs to a group, checks the group's **shared gate**
   (`QueueGroup.canProceed(now)`), which is open only when both:
   - the group is below its concurrency ceiling
     (`activeCount < maxParallelInGroup`), **and**
   - the group's shared cool-down has elapsed (`now >= wakeTime`);
3. if the gate is open, hands out a URI from the queue, moves the queue to
   *in-process*, and calls `QueueGroup.acquire()` plus `advanceRotation()` so
   the round-robin pointer moves to the next member;
4. when the fetch finishes, `releaseGroup()` calls `QueueGroup.release()`,
   which frees the slot and arms the shared cool-down (`wakeTime = now +
   groupMinDelayMs`).

Queues that are not part of any group skip steps 2–4 entirely and follow the
classic per-host politeness path unchanged.

### Gate closed: snooze vs. park

When the shared gate is **closed** in step 2, the reason matters, and the
frontier reacts differently:

- **Cool-down active** (`wakeTime` is in the future): the queue is **snoozed**
  until that real wake time. This is a genuine time-based wait, so
  `snoozedClassQueues` is exactly the right place for it.
- **Concurrency ceiling only** (`wakeTime` already in the past, but
  `activeCount == maxParallelInGroup`): there is *no* natural wake time — what
  will unblock the queue is not the clock but a **sibling releasing a slot**.
  Snoozing "for 1 ms" here would create a busy `snoozed ↔ ready` polling cycle.
  Instead the queue is **parked** on the group's waiting list
  (`QueueGroup.park()`), and no arbitrary snooze is scheduled.

### Event-driven wake-up from the waiting list

The waiting list turns slot contention into an **event-driven** mechanism
instead of polling:

- When a member finishes, `releaseGroup()` frees the slot and then calls
  `unparkOne()`, which pops the next parked member (via
  `QueueGroup.pollWaiting()`, honoring the round-robin rotation from
  `rotationIndex`) and puts it back in circulation:
  - if a cool-down is now armed, the popped queue is **snoozed** until
    `wakeTime` (a real date), never busy-looped;
  - otherwise it is returned to **ready** immediately.
- **Lock ordering:** `findEligibleURI()` locks a queue and then its group, so
  `unparkOne()` must never lock a queue while holding the group lock. The pop
  is atomic on the group alone; the popped queue is then handled outside any
  group lock.
- **Safety nets against lost wake-ups:** `wakeQueues()` also calls
  `wakeParkedGroupQueues()`, which unparks one queue per group whose gate is
  actually open, and the operator action `forceWakeQueues()` drains every
  group's waiting list. A queue that is retired or relocated while parked is
  removed from the list (`QueueGroup.unpark()` in `retireQueue()`).

The waiting list is **transient**: like the rotation state, it is not persisted
and is rebuilt naturally after a restart or checkpoint.

### Reporting

The frontier's short report exposes the parked queues under the
`groupWaitingQueues` key, and includes them in the `activeQueues` total
(`inProcessQueues + readyQueues + snoozedQueues + groupWaitingQueues`), so
queues held back by a group's concurrency ceiling remain visible and accounted
for.

## How members are matched: the `classKey`

Group members are matched against the frontier's queue **`classKey`**, whose
format depends on the configured `queueAssignmentPolicy`.

The default policy, `SurtAuthorityQueueAssignmentPolicy`, produces **SURT-form
authority** classKeys. For example:

| URL                              | classKey                 |
|----------------------------------|--------------------------|
| `http://commission.europa.eu/`   | `eu,europa,commission,`  |
| `http://data.europa.eu/`         | `eu,europa,data,`        |
| `http://www.example.com:8080/`   | `com,example,www,#8080`  |


> **Important:** choose your member declaration style to match the `classKey`
> format produced by your `queueAssignmentPolicy`. With the default
> `SurtAuthorityQueueAssignmentPolicy`, use `groupMembersBySurt`. The
> `groupMembersByHost` and `groupMembersByRegex` forms match plain hostnames and
> are only appropriate when a host-based policy (e.g.
> `HostnameQueueAssignmentPolicy`) is used.

## Declaring queue groups

A queue group is declared through the `queueGroups` bean (a
`QueueGroupManager`), which holds a list of `QueueGroup` beans.

Each `QueueGroup` supports the following properties:

| Property               | Type           | Default | Description                                                             |
|------------------------|----------------|---------|-------------------------------------------------------------------------|
| `name`                 | String         | —       | Human-readable, unique name of the group.                               |
| `maxParallelInGroup`   | int            | `1`     | Max member queues fetched simultaneously (shared concurrency).          |
| `groupMinDelayMs`      | long (ms)      | `0`     | Minimal shared delay between two fetches of the group.                  |
| `groupMembersByHost`   | List<String>   | empty   | Exact host match (host-based policies only).                            |
| `groupMembersByRegex`  | List<Pattern>  | empty   | Full regex match against the host part of the classKey.                 |
| `groupMembersBySurt`   | List<String>   | empty   | classKey starts with the given SURT-authority prefix (default policy).  |
| `groupMembersByDecideRule` | DecideRule | none    | A `DecideRule` evaluated against the real `CrawlURI`; `ACCEPT` means member (policy-independent). |

A queue belongs to
the group if it matches **any** member declaration.

### Example 1 — By SURT prefix (default policy)

Recommended with the default `SurtAuthorityQueueAssignmentPolicy`. A member
matches when the `classKey` **starts with** the given SURT-authority prefix.

The prefix `eu,europa,` groups all `*.europa.eu` queues
(`commission.europa.eu` -> `eu,europa,commission,`,
`data.europa.eu` -> `eu,europa,data,`, ...).

```xml
<bean id="queueGroups" class="org.archive.crawler.frontier.QueueGroupManager">
 <property name="groups">
  <list>
   <bean class="org.archive.crawler.frontier.QueueGroup">
    <property name="name" value="europa_eu" />
    <property name="maxParallelInGroup" value="1" />
    <property name="groupMinDelayMs" value="4000" />
    <property name="groupMembersBySurt">
     <list>
      <value>eu,europa,</value>
     </list>
    </property>
   </bean>
  </list>
 </property>
</bean>
```

To group only two specific hosts (instead of a whole domain), use their full
SURT authorities:

```xml
<property name="groupMembersBySurt">
 <list>
  <value>eu,europa,commission,</value>
  <value>eu,europa,data,</value>
 </list>
</property>
```

### Example 2 — By exact host (host-based policy)

Use `groupMembersByHost` only when a host-based `queueAssignmentPolicy` (e.g.
`HostnameQueueAssignmentPolicy`) is configured, so that classKeys are plain
hostnames. A member matches when the `classKey` equals the host, or begins with
`host#` (port) or `host+` (subqueue).

```xml
<bean id="queueGroups" class="org.archive.crawler.frontier.QueueGroupManager">
 <property name="groups">
  <list>
   <bean class="org.archive.crawler.frontier.QueueGroup">
    <property name="name" value="europa_eu_hosts" />
    <property name="maxParallelInGroup" value="1" />
    <property name="groupMinDelayMs" value="4000" />
    <property name="groupMembersByHost">
     <list>
      <value>european-union.europa.eu</value>
      <value>commission.europa.eu</value>
      <value>data.europa.eu</value>
      <value>op.europa.eu</value>
     </list>
    </property>
   </bean>
  </list>
 </property>
</bean>
```

### Example 3 — By regex (host-based policy)

Use `groupMembersByRegex` only with a host-based `queueAssignmentPolicy`. The
pattern must **fully match** the host part of the classKey (the substring before
any `#` or `+`).

```xml
<bean id="queueGroups" class="org.archive.crawler.frontier.QueueGroupManager">
 <property name="groups">
  <list>
   <bean class="org.archive.crawler.frontier.QueueGroup">
    <property name="name" value="europa_eu_regex" />
    <property name="maxParallelInGroup" value="1" />
    <property name="groupMinDelayMs" value="4000" />
    <property name="groupMembersByRegex">
     <list>
      <value>.*\.europa\.eu</value>
     </list>
    </property>
   </bean>
  </list>
 </property>
</bean>
```

### Example 4 — By DecideRule (any queueAssignmentPolicy)

Use `groupMembersByDecideRule` to let an arbitrary `DecideRule` decide
membership. Unlike the three matchers above, the rule is evaluated against the
real `CrawlURI` (not the bare `classKey`), so it works regardless of the
configured `queueAssignmentPolicy`. Membership is resolved when a URI is first
enqueued and then **cached per `classKey`**, so all subsequent URIs of that
queue keep the same group. A URI is a member when the rule returns `ACCEPT`.

```xml
<bean id="queueGroups" class="org.archive.crawler.frontier.QueueGroupManager">
 <property name="groups">
  <list>
   <bean class="org.archive.crawler.frontier.QueueGroup">
    <property name="name" value="example_org_by_rule" />
    <property name="maxParallelInGroup" value="1" />
    <property name="groupMinDelayMs" value="4000" />
    <property name="groupMembersByDecideRule">
     <bean class="org.archive.modules.deciderules.MatchesRegexDecideRule">
      <property name="regex" value="https?://([^/]+\.)?example\.org/.*" />
     </bean>
    </property>
   </bean>
  </list>
 </property>
</bean>
```

You may also compose richer conditions with a `DecideRuleSequence`. Because the
rule needs a `CrawlURI`, prefer this form when host/SURT/regex matching on the
`classKey` is not expressive enough for your needs.

**Checkpoint persistence.** Because DecideRule-based membership can only be
resolved from a real `CrawlURI` (and not from the bare `classKey`), the
resolved `classKey → group` mapping is **persisted with the checkpoint** and
re-bound to the configured groups on restore (by group name). This means a
restored queue keeps its group coordination (shared concurrency, shared delay,
round-robin) even if no new URI of that queue is enqueued after the restore.
Entries whose group was renamed or removed in the configuration since the
checkpoint are dropped on restore.

### Example 5 — Multiple groups and combined matchers

You can declare several groups, and combine member lists within a group.

```xml
<bean id="queueGroups" class="org.archive.crawler.frontier.QueueGroupManager">
 <property name="groups">
  <list>
   <!-- One shared visitor for all *.europa.eu (default SURT policy). -->
   <bean class="org.archive.crawler.frontier.QueueGroup">
    <property name="name" value="europa_eu" />
    <property name="maxParallelInGroup" value="1" />
    <property name="groupMinDelayMs" value="4000" />
    <property name="groupMembersBySurt">
     <list>
      <value>eu,europa,</value>
     </list>
    </property>
   </bean>

   <!-- Allow up to 2 parallel fetches across a couple of example hosts. -->
   <bean class="org.archive.crawler.frontier.QueueGroup">
    <property name="name" value="example_sites" />
    <property name="maxParallelInGroup" value="2" />
    <property name="groupMinDelayMs" value="1000" />
    <property name="groupMembersBySurt">
     <list>
      <value>com,example,</value>
      <value>org,example,</value>
     </list>
    </property>
   </bean>
  </list>
 </property>
</bean>
```

## Changing members at runtime (scripting console)

Group members can be added **and removed while the crawl is running**, from the
Heritrix scripting console, through the `QueueGroupManager` bean:

| Method                                        | Effect                                                   |
|-----------------------------------------------|----------------------------------------------------------|
| `addHostMember(group, host)`                  | adds an exact-host matcher to the group                  |
| `addRegexMember(group, regex)`                | adds a regex matcher to the group                        |
| `addSurtMember(group, surtPrefix)`            | adds a SURT-prefix matcher to the group                  |
| `removeHostMember(group, host)`               | removes an exact-host matcher from the group             |
| `removeRegexMember(group, regex)`             | removes a regex matcher, identified by its pattern string |
| `removeSurtMember(group, surtPrefix)`         | removes a SURT-prefix matcher from the group             |

Each method returns `true` if the group exists (and, for `remove*`, if the
matcher was actually present), and `false` otherwise (unknown group, unknown
matcher or `null` value: nothing is changed).

```groovy
def mgr = appCtx.getBean("queueGroups")

// stop treating data.europa.eu as part of the "europa_eu_sites" group
println mgr.removeSurtMember("europa_eu_sites", "eu,europa,data,")
```

For `removeRegexMember`, pass exactly the same pattern string as the one that was
declared (e.g. `.*\.europa\.eu`).

### What happens when a member is removed

What is removed is a **matcher**, so every queue that matched it (and no other
matcher of the group) leaves the group:

1. **Cache invalidation** — the cached `classKey → group` resolutions (and the
   mapping persisted for checkpoints) are cleared, so each queue is resolved
   again on its next lookup. A queue that no longer matches any group becomes
   **ungrouped** and follows the classic per-host politeness path.
2. **Rotation clean-up** — the discovered members that no longer match any
   classKey-based matcher of the group are dropped from the round-robin rotation
   (the rotation pointer is brought back in range if needed), so the rotation
   never waits for a queue that has left the group. Queues that still match
   another matcher of the group are kept.
3. **Queue currently in process** — it keeps the shared slot it acquired. When
   its URI finishes, `releaseGroup()` releases **the group it actually
   acquired**, not the one it now resolves to, so the group's `activeCount`
   never leaks and never goes out of sync, and the shared cool-down is armed as
   usual for that last fetch.
4. **Queue parked on the group's waiting list** — it is not lost: the existing
   mechanisms (`unparkOne()` on the next slot release, and the
   `wakeParkedGroupQueues()` safety net run by `wakeQueues()`) pop it from the
   waiting list and put it back in circulation. Once back in `findEligibleURI()`
   it is no longer grouped and is emitted without group gating.
5. **Other queues (ready, snoozed, inactive)** — they are not touched; they
   simply continue as ungrouped queues the next time they are handled.

A removal never deletes any URI and never touches the `WorkQueue` itself; only
its group membership changes.

> **Limitations**
>
> - Runtime changes are **not persisted in the crawl configuration**: they
>   apply to the running job only. After a restart, the groups are rebuilt from
>   the `queueGroups` bean as declared in the crawler beans file (edit it to make
>   a change permanent).
> - `groupMembersByDecideRule` cannot be edited this way. Membership obtained
>   through a `DecideRule` is re-evaluated against a real `CrawlURI`, so after a
>   cache invalidation it is only re-established the next time a URI of that
>   queue is enqueued. The same applies, after any group modification (addition
>   or removal), to queues matched by a `DecideRule`. In the meantime, such
>   queues are also absent from the rotation clean-up check (step 2), which only
>   considers classKey-based matchers.

## Limitation: precedence is not arbitrated within a group

Queue **precedence** is only honored *upstream*, when an inactive queue is
promoted to ready (`activateInactiveQueue()` walks the inactive queues in
precedence order). The group logic — the shared politeness gate and the
round-robin rotation — operates **only on queues that are already ready** and is
**precedence-agnostic**:

- the rotation cycles over the group's member queues in their *discovery*
  (first-seen) order, not in precedence order;
- the readiness check used by the rotation only tests whether a member queue is
  currently ready, never its precedence.

As a consequence, **mixing queues that are meant to have different precedences
in the same group is not supported**: within a group, a lower-precedence member
may be served before a higher-precedence one, because the round-robin treats all
members as equivalent. If you rely on precedence to prioritize some hosts over
others, keep those hosts in **separate** groups (or outside any group) rather
than grouping queues of differing precedence together.

## Choosing the matcher style

| Matcher                | Matches against         | Use when the queueAssignmentPolicy is ...                |
|------------------------|-------------------------|----------------------------------------------------------|
| `groupMembersBySurt`   | whole `classKey` (prefix)| `SurtAuthorityQueueAssignmentPolicy` (default)          |
| `groupMembersByHost`   | host part (exact)       | host-based (e.g. `HostnameQueueAssignmentPolicy`)         |
| `groupMembersByRegex`  | host part (full match)  | host-based (e.g. `HostnameQueueAssignmentPolicy`)         |
| `groupMembersByDecideRule` | the real `CrawlURI`  | any policy (resolved at enqueue, cached per classKey)    |

If a member declaration never matches, check that its style is consistent with
the classKey format produced by your active `queueAssignmentPolicy`
(`groupMembersByDecideRule` is exempt, since it matches the URI itself).
