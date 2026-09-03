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

### Example 4 — Multiple groups and combined matchers

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

## Choosing the matcher style

| Matcher                | Matches against         | Use when the queueAssignmentPolicy is ...                |
|------------------------|-------------------------|----------------------------------------------------------|
| `groupMembersBySurt`   | whole `classKey` (prefix)| `SurtAuthorityQueueAssignmentPolicy` (default)          |
| `groupMembersByHost`   | host part (exact)       | host-based (e.g. `HostnameQueueAssignmentPolicy`)         |
| `groupMembersByRegex`  | host part (full match)  | host-based (e.g. `HostnameQueueAssignmentPolicy`)         |

If a member declaration never matches, check that its style is consistent with
the classKey format produced by your active `queueAssignmentPolicy`.
