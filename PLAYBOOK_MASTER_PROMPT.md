# Master Prompt: Technical Topic Playbook Generator (MkDocs Material)

A reusable prompt that builds a complete MkDocs playbook for **any** technical topic: Java, Angular, Jakarta EE, Spring Boot, React, Kubernetes, Apache Kafka, PostgreSQL, AWS, System Design and so on. The playbook takes a reader from their first day to the judgement of an engineer with 15 years of experience, and every topic and subtopic gets a description, how it works, examples, pros, cons, best practices and pitfalls where they make sense.

The prompt has two parts:

- **Input fields** at the top. You fill these in for each topic (topic, version, example language, depth, levels, site name and so on). Only `TOPIC` is required, and every other field has a default.
- **A common part** that stays the same for every topic: the Material for MkDocs theme and site config, the folder layout, the page template, the quality rules and the batch plan. Every playbook you generate looks and works the same way.

It works in phases (inputs, syllabus, scaffold, pages, verification), so long outputs don't get cut off and you can resume in a new chat.

The [Java design patterns prompt](MASTER_PROMPT.md) is a topic-specific version of the same idea.

---

````text
######################################################################
#  INPUT FIELDS (fill these in; leave a field empty to use its default)
######################################################################
TOPIC              = {{e.g. Spring Boot | Angular | Java | Jakarta EE | Kubernetes}}
                     (REQUIRED)
VERSION            = {{e.g. 4.0 | 20 | 25 | 11}}
                     default: latest stable version you reliably know
EXAMPLE_LANGUAGE   = {{e.g. Java 21 | TypeScript 5 | YAML + kubectl | SQL}}
                     default: the topic's primary language
COMPANION_STACK    = {{e.g. Maven, PostgreSQL, Kafka, Docker, JUnit 5, Testcontainers}}
                     default: the topic's most common production stack
TARGET_LEVELS      = {{L0-L5}}
                     default: L0-L5 (Day 0 to 15-year expert)
AUDIENCE_EMPHASIS  = {{balanced | beginner-heavy | expert-heavy}}
                     default: balanced
DEPTH              = {{core (~40-60 pages) | exhaustive (~80-150 pages)}}
                     default: exhaustive
RUNNING_DOMAIN     = {{e.g. e-commerce | banking | grocery delivery | healthcare}}
                     default: e-commerce
INCLUDE_INTERVIEW  = {{yes | no}}            default: yes
INCLUDE_CAPSTONE   = {{yes | no}}            default: yes
EXTRA_TOPICS       = {{topics that must be covered, comma-separated}}
                     default: none
EXCLUDE_TOPICS     = {{topics to leave out, comma-separated}}
                     default: none
SITE_NAME          = {{e.g. Spring Boot 4 Playbook}}
                     default: "<TOPIC> <VERSION> Playbook"
SITE_URL           = {{e.g. https://<user>.github.io/<repo>/}}
                     default: https://example.github.io/<topic-slug>-playbook/
REPO_URL           = {{e.g. https://github.com/<user>/<repo>}}
                     default: https://github.com/your-org/<topic-slug>-playbook
PRIMARY_COLOR      = {{any Material palette name: indigo, green, blue, teal, red...}}
                     default: indigo
ACCENT_COLOR       = {{any Material accent name: deep purple, amber, cyan...}}
                     default: deep purple
OUTPUT_MODE        = {{write-to-disk | print-in-chat}}
                     default: write-to-disk if you have file tools, else print-in-chat
PAGES_PER_RESPONSE = {{1 | 2 | 3}}           default: 2
######################################################################
#  END OF INPUT FIELDS. Everything below is the common part: don't edit.
######################################################################

ROLE
You are a Principal Engineer and technical author. You have 15+ years of hands-on,
production experience with <TOPIC>, you've led design reviews and hiring loops, and
you've mentored engineers from interns to staff level. You write clearly for beginners
without leaving out the depth an expert expects. You never pad, and you never invent
facts.

GOAL
Build a complete, production-quality documentation site, a "playbook", for <TOPIC>
<VERSION>, using MkDocs with the Material for MkDocs theme configured exactly as in
Phase 2. The playbook covers the levels in TARGET_LEVELS (by default from zero
knowledge to an engineer with 15 years of experience), and it must cover EVERY topic
and subtopic of the subject, each with a description, how it works, examples, pros,
cons, best practices and pitfalls where they apply.

In the rest of this prompt, <FIELD_NAME> means the resolved value of that input field.

======================================================================
PHASE 0 — RESOLVE THE INPUTS
======================================================================
1. Read the INPUT FIELDS. A field still showing {{...}} or left empty uses its default.
2. If TOPIC is missing, ask for it in one short question and wait. Ask nothing else.
3. Print a table of the resolved inputs (field | value | "default" or "given"), so
   the user can see what you'll use. Derive <topic-slug> as a kebab-case slug of
   TOPIC (e.g. "Spring Boot" → spring-boot).
4. If the user's values conflict (e.g. EXAMPLE_LANGUAGE doesn't fit TOPIC), say so
   and use the sensible choice.
5. If VERSION may be newer than your training data (for example a major release from
   the last year), say so plainly. Then: (a) use what you reliably know, (b) mark
   every claim you're not sure of with the `!!! warning "Verify against the official
   docs"` admonition, and (c) never invent APIs, property names, CLI flags,
   annotations or version numbers. If you can browse, check the official
   documentation and release notes first and cite them.
6. Continue straight to Phase 1 in the same response.

======================================================================
PHASE 1 — CURRICULUM MAP (the exhaustive topic list; then wait)
======================================================================
Before writing any page, produce the complete syllabus. This is the most important
step, because the playbook can't cover what the syllabus misses.

1A. Build the list by sweeping ALL of these sources and lenses, and say which you used:
    - The official documentation's table of contents and reference guide.
    - Release notes / "what's new" / migration guides for the last 2–3 major versions.
    - The core concepts and the internals (how it actually works under the hood).
    - Configuration, CLI, APIs, extension points and plugin/module ecosystem.
    - Data, state, persistence and integration with other systems.
    - Testing at every level (unit, integration, contract, end-to-end, load).
    - Security (authn/authz, secrets, supply chain, hardening, common CVE classes).
    - Performance, scalability, capacity planning, tuning and benchmarking.
    - Observability (logs, metrics, traces, health checks) and operations
      (deployment, upgrades, rollback, backup/restore, incident response).
    - Architecture and design: patterns, anti-patterns, trade-offs against
      alternatives, and when NOT to use the technology.
    - Developer experience: tooling, IDE support, build, local development, debugging.
    - Migration: from older versions and from competing technologies.
    - Cloud / container / platform deployment specifics.
    - Organisational topics an expert owns: standards, code review checklists,
      governance, cost, team enablement, technical-debt strategy.

1B. Organise the syllabus into these levels (only the ones in TARGET_LEVELS). Each
    level is a top-level nav section:

    | Level | Name                    | Experience | The reader can…                                  |
    |-------|-------------------------|------------|--------------------------------------------------|
    | L0    | Orientation             | Day 0      | explain what it is, why it exists, set it up     |
    | L1    | Foundations             | 0–1 yr     | build a simple working thing with guidance       |
    | L2    | Practitioner            | 1–3 yrs    | ship features independently, test them           |
    | L3    | Professional            | 3–6 yrs    | design components, debug production, tune        |
    | L4    | Senior / Lead           | 6–10 yrs   | own systems, make trade-offs, lead reviews       |
    | L5    | Staff / Principal       | 10–15+ yrs | set direction, internals, scale, org-wide impact |

    Every topic is placed at the level where a reader first NEEDS it. A topic can
    reappear at a higher level as an "advanced" page (e.g. "Transactions" at L2 and
    "Distributed transactions & consistency" at L4); link the two.

1C. Present the syllabus as a nested list: Level → Topic (one page each) →
    Subtopics (H2/H3 sections inside that page). Give each topic a one-line purpose
    and an estimated page count. Then list, separately:
    - "Deliberately excluded" topics with a reason (deprecated, out of scope, niche).
    - The cross-cutting pages (see Phase 2).
    - Where each EXTRA_TOPICS item landed, and confirmation that EXCLUDE_TOPICS are
      left out.
    - The depth you used (DEPTH) and how it shaped the page count.
    - The total page count and the batch plan (Phase 4).

1D. Run a completeness check and print it: "Could a 15-year expert point at any
    feature, setting, failure mode or decision the syllabus doesn't cover?" List what
    you added because of this check.

Then STOP and ask: "Syllabus ready. Reply `approve`, or tell me what to add, remove or
move." Apply changes and re-print only the changed parts.

======================================================================
PHASE 2 — SITE SCAFFOLD (COMMON: identical for every topic)
======================================================================
2A. Folder structure. Create level folders only for TARGET_LEVELS, interview/ only if
    INCLUDE_INTERVIEW = yes, and projects/ only if INCLUDE_CAPSTONE = yes:

<topic-slug>-playbook/
├── mkdocs.yml
├── requirements.txt
├── README.md                          # what it is, how to run, how to contribute
├── .github/workflows/docs.yml         # build --strict on PRs; deploy on push to main
├── docs/
│   ├── index.md                       # hero, "choose your path" cards, level map
│   ├── how-to-use.md                  # learning paths per audience, page anatomy
│   ├── roadmap.md                     # Mermaid roadmap L0→L5 + skills checklist per level
│   ├── l0-orientation/                # index.md + one .md per topic
│   ├── l1-foundations/
│   ├── l2-practitioner/
│   ├── l3-professional/
│   ├── l4-senior/
│   ├── l5-staff-principal/
│   ├── reference/
│   │   ├── cheatsheet.md              # one-page summary of commands/APIs/config
│   │   ├── configuration.md           # every important setting: default, effect, when to change
│   │   ├── comparisons.md             # vs. the 3–5 main alternatives, with a decision table
│   │   ├── decision-guide.md          # "which option when?" Mermaid decision trees
│   │   ├── anti-patterns.md           # catalogue of anti-patterns with fixes
│   │   ├── production-checklist.md    # go-live checklist as task lists
│   │   ├── migration-guide.md         # from previous major versions / competitors
│   │   └── version-history.md         # what changed in each major version and why
│   ├── projects/
│   │   ├── index.md
│   │   └── capstone.md                # one end-to-end project in <RUNNING_DOMAIN>
│   │                                  # that uses most of the playbook's topics
│   ├── interview/                     # only if INCLUDE_INTERVIEW = yes
│   │   ├── index.md                   # how rounds work, what each level is judged on
│   │   ├── questions-by-level.md      # graded L1–L5, model answers
│   │   ├── scenario-questions.md      # "production is down, what do you do?" style
│   │   └── hands-on-tasks.md          # take-home / live-coding style tasks
│   ├── glossary.md                    # every term, linked from pages via abbr
│   ├── further-reading.md             # official docs, specs, books, talks (real only)
│   ├── tags.md
│   └── stylesheets/extra.css
└── examples/                          # runnable example project(s), if the topic has code
    └── ...                            # one folder per level or per capstone module

2B. mkdocs.yml — use EXACTLY this file. Only replace the <FIELD> values and fill in
    `nav:` from the approved syllabus. Don't add, remove or reorder anything else.

```yaml
site_name: <SITE_NAME>
site_description: <TOPIC> <VERSION> from Day 0 to expert, with runnable examples, trade-offs and best practices.
site_url: <SITE_URL>
repo_url: <REPO_URL>
repo_name: <topic-slug>-playbook
edit_uri: edit/main/docs/
copyright: Copyright &copy; <current year> <SITE_NAME>

theme:
  name: material
  language: en
  icon:
    logo: material/book-open-page-variant
    repo: fontawesome/brands/github
  font:
    text: Inter
    code: JetBrains Mono
  palette:
    - media: "(prefers-color-scheme)"
      toggle:
        icon: material/brightness-auto
        name: Switch to light mode
    - media: "(prefers-color-scheme: light)"
      scheme: default
      primary: <PRIMARY_COLOR>
      accent: <ACCENT_COLOR>
      toggle:
        icon: material/weather-sunny
        name: Switch to dark mode
    - media: "(prefers-color-scheme: dark)"
      scheme: slate
      primary: <PRIMARY_COLOR>
      accent: <ACCENT_COLOR>
      toggle:
        icon: material/weather-night
        name: Switch to system preference
  features:
    - announce.dismiss
    - content.code.annotate
    - content.code.copy
    - content.code.select
    - content.tabs.link
    - content.tooltips
    - navigation.footer
    - navigation.indexes
    - navigation.instant
    - navigation.instant.progress
    - navigation.path
    - navigation.sections
    - navigation.tabs
    - navigation.tabs.sticky
    - navigation.top
    - navigation.tracking
    - search.highlight
    - search.share
    - search.suggest
    - toc.follow

plugins:
  - search
  - tags

markdown_extensions:
  - abbr
  - admonition
  - attr_list
  - def_list
  - footnotes
  - md_in_html
  - tables
  - toc:
      permalink: true
  - pymdownx.betterem
  - pymdownx.caret
  - pymdownx.details
  - pymdownx.emoji:
      emoji_index: !!python/name:material.extensions.emoji.twemoji
      emoji_generator: !!python/name:material.extensions.emoji.to_svg
  - pymdownx.highlight:
      anchor_linenums: true
      line_spans: __span
      pygments_lang_class: true
  - pymdownx.inlinehilite
  - pymdownx.keys
  - pymdownx.mark
  - pymdownx.smartsymbols
  - pymdownx.snippets:
      auto_append:
        - includes/abbreviations.md
  - pymdownx.superfences:
      custom_fences:
        - name: mermaid
          class: mermaid
          format: !!python/name:pymdownx.superfences.fence_code_format
  - pymdownx.tabbed:
      alternate_style: true
  - pymdownx.tasklist:
      custom_checkbox: true
  - pymdownx.tilde

extra:
  social:
    - icon: fontawesome/brands/github
      link: <REPO_URL>

extra_css:
  - stylesheets/extra.css

watch:
  - includes

nav:
  - Home: index.md
  - How to use: how-to-use.md
  - Roadmap: roadmap.md
  # one section per level in TARGET_LEVELS, then Reference, Projects,
  # Interview, Glossary, Further reading, Tags, matching the approved syllabus
```

    Put includes/abbreviations.md at the project root (next to mkdocs.yml, not in
    docs/), so it's appended to every page but isn't built as a page itself.

2C. requirements.txt — use EXACTLY this (versions known to build the config above;
    only bump them if you know newer stable versions are compatible):

```text
mkdocs==1.6.1
mkdocs-material==9.7.7
pymdown-extensions==10.21
```

    docs/stylesheets/extra.css — same classes on every playbook: .hero (gradient
    banner using var(--md-primary-fg-color) and var(--md-accent-fg-color)),
    .grid.cards tweaks, level badges .badge-l0 … .badge-l5 (L0 ⚪ Orientation,
    L1 🟢 Foundations, L2 🔵 Practitioner, L3 🟡 Professional, L4 🟠 Senior,
    L5 🔴 Staff/Principal), and a .read-time pill. Use only Material CSS variables
    so it works in light and dark mode.

2D. .github/workflows/docs.yml — use this, and add one job that builds and tests
    examples/ with the topic's normal toolchain if the topic has code:

```yaml
name: Docs
on:
  push:
    branches: [main]
  pull_request:
permissions:
  contents: write
jobs:
  docs:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
          cache: pip
      - run: pip install -r requirements.txt
      - name: Build (fails on broken links or nav mistakes)
        run: mkdocs build --strict
      - name: Deploy to GitHub Pages
        if: github.event_name == 'push' && github.ref == 'refs/heads/main'
        run: mkdocs gh-deploy --force
```
2E. docs/index.md: hero banner, "Choose your path" cards (New to <TOPIC> /
    Working with <TOPIC> / Leading with <TOPIC> / Interview sprint, the last one only
    if INCLUDE_INTERVIEW = yes), a Mermaid map of
    the six levels, and a "How every page is structured" section.
2F. docs/roadmap.md: a Mermaid flowchart of the levels, and for each level a task-list
    of "you're ready for the next level when you can…" skills.

======================================================================
PHASE 3 — MANDATORY TEMPLATE FOR EVERY TOPIC PAGE
======================================================================
Every topic page must follow this structure, with these headings, in this order.
Sections marked (if relevant) may be skipped only when they truly don't apply;
when you skip one, don't leave an empty heading.

---
tags: [<level>, <area e.g. core | data | security | testing | operations | performance>]
---
# <Topic Name>

<level badge> · ⏱ <minutes> min read · Prerequisites: <links to earlier pages>

!!! abstract "TL;DR"
    Two or three sentences: what it is, why it matters, when you'd use it.

## 1. What It Is
   Plain-English description, then the precise/official definition. Define every new
   term (add it to the glossary).

## 2. Why It Exists
   The problem it solves, what people did before it, and what goes wrong without it.

## 3. Mental Model / Analogy
   A real-world analogy for beginners, plus the correct technical mental model.

## 4. How It Works
   The mechanism step by step, with at least one Mermaid diagram (flowchart,
   sequence, state or class diagram, whichever explains it best). For L3+ pages,
   include the internals: lifecycle, threading/concurrency model, memory, I/O,
   ordering and failure behaviour.

## 5. Subtopics
   One H3 per subtopic from the syllabus. EVERY subtopic gets:
   - **Description:** what it is and what it does.
   - **How to use it:** the minimal code/config/command.
   - **Pros** and **Cons** (as two short bullet lists, or a table) whenever the
     subtopic is a choice, option, feature or approach. For pure facts (e.g. "the
     default port"), skip pros/cons.
   - **Best practice:** one or two lines.
   - **Gotcha:** the most common mistake, if there is one.

## 6. Examples (exactly 3, progressively harder)
   ### Example 1: Minimal (L-level of this page)
   ### Example 2: Realistic (in <RUNNING_DOMAIN>)
   ### Example 3: Production-grade (error handling, config, observability, tests)
   For EACH example:
   **Scenario:** 2–5 sentences of context and constraints.
   **Code / Config:** complete and runnable as shown. Split multiple files into
   `=== "file-name.ext"` content tabs. Include imports/package lines, dependency
   coordinates and versions. Use code annotations (# (1)!) for the important lines.
   No pseudo-code, no "..." and no "rest omitted".
   **Run it:** the exact command(s).
   **Output:** the exact console output / HTTP response / logs, in a ```text block,
   matching what the code prints. Show a failure-path output too when the topic is
   about errors, resilience or validation.
   **What to notice:** 3–5 bullets.

## 7. Pros, Cons & Trade-offs
   A table with columns: Aspect | Pros | Cons. Cover at least: simplicity,
   performance, scalability, operability, security, testability, cost, learning curve.

## 8. When to Use / When NOT to Use
   Two lists, and the alternatives to choose instead (link to comparisons.md).

## 9. Best Practices
   A numbered list of concrete, opinionated rules ("Do X because Y"). Mark the ones
   that are industry consensus vs. your opinion.

## 10. Pitfalls & Anti-Patterns
   `!!! danger` blocks: symptom → root cause → fix. Include at least one production
   failure mode.

## 11. Configuration & Tuning (if relevant)
   Table: setting | default | what it does | when to change it | risk of changing.

## 12. Security Considerations (if relevant)
## 13. Performance & Scalability (if relevant)
   Numbers only when you're confident of them; otherwise describe how to measure.
## 14. Testing It (if relevant)
   How to test code that uses this topic, with one example test.
## 15. Observability & Operations (if relevant)
   What to log, which metrics to watch, alerts to set, how to debug it in production.

## 16. Version Notes
   What changed in recent major versions, what's deprecated, and the migration path.
   Flag anything newer than your reliable knowledge with "Verify against the
   official docs".

## 17. Expert Deep Dive (L4–L5 perspective, on EVERY page, even L0–L1 topics)
   What a 10–15-year engineer knows about this topic that a 2-year engineer doesn't:
   edge cases, internals, scale effects, organisational impact, how to defend or
   challenge it in a design review, and how to migrate legacy systems to it. Include
   one realistic "war story": a production incident, how it was diagnosed, the fix,
   and the lesson.

## 18. Interview Corner (only if INCLUDE_INTERVIEW = yes)
   Content tabs:
   === "🟢 L1–L2"   5 Q&A (definitions, basic usage, differences)
   === "🟡 L3"      5 Q&A (debugging, design of a component, trade-offs)
   === "🔴 L4–L5"   5 Q&A (open-ended design, scale, failure, follow-up chains like
                     "now 10x the traffic", "now make it multi-region")
   === "💻 Hands-on" 1 task with the solution hidden in a `??? success "Solution"` block
   Each answer is a model answer a candidate could say out loud, with the key phrase
   interviewers listen for in **bold**.

## 19. Practice
   A task list of 3–5 exercises, from easy to hard, with hints in `??? tip` blocks.

## 20. Quick Revision Card
   !!! tip "Remember in 30 seconds"
   5 bullets + one code/config/command skeleton.

## 21. Further Reading
   Only real, well-known sources: official docs pages, specs/RFCs/JEPs/KEPs,
   well-known books and talks. Never invent URLs; if you're not sure of the exact URL,
   name the source without a link.

Footer: "← Previous" / "Next →" links that follow the nav order.

Each level's index.md must contain: the level's goal, who it's for, a table of its
pages with one-line descriptions, a Mermaid map of how the pages relate, and the
"ready for the next level" checklist.

======================================================================
PHASE 4 — EXECUTION PLAN (follow strictly)
======================================================================
Step 1: Phase 0 resolve inputs (ask only if TOPIC is missing).
Step 2: Phase 1 syllabus → wait for `approve`.
Step 3: Phase 2 scaffold in full (mkdocs.yml, requirements.txt, README, workflow,
        index.md, how-to-use.md, roadmap.md, extra.css, includes/abbreviations.md,
        tags.md, every level's index.md, and empty-but-valid stubs for every page in
        the nav so `mkdocs build --strict` passes from the start).
Step 4: Write ONE complete reference page (the first L1 topic) using the template,
        then STOP and ask: "Reference page done. Reply `continue`, or tell me what to
        change in the template before I write the rest."
Step 5: On `continue`, write the pages level by level: L0 → L1 → L2 → L3 → L4 → L5 →
        reference/ → projects/capstone → interview/ → glossary → further-reading.
        Write at most <PAGES_PER_RESPONSE> topic pages per response so nothing gets truncated. If a page
        would be too long for one response, split it at a section boundary and say
        "Page X continues in the next response".
Step 6: End EVERY response with a progress tracker:
          Progress: <done>/<total> pages (<percent>%)
          ✅ done pages  ·  ⏳ next pages  ·  ⬜ remaining pages (grouped by level)
        and the single word the user should reply to continue.
Step 7: When all pages are done, run the final verification (Phase 5) and print
        the local commands:
          python -m venv .venv && source .venv/bin/activate
          pip install -r requirements.txt
          mkdocs serve          # http://127.0.0.1:8000
          mkdocs build --strict
        plus the commands to build/test the examples, and how to publish on GitHub
        Pages (Settings → Pages → Deploy from branch → gh-pages).

ADAPTING TO THE TOPIC TYPE: the template is the same for every topic, but pick the
right "code" for it. Programming languages (Java, Python, Go): language programs and
tests. Frameworks (Spring Boot, Angular, Jakarta EE, React): small runnable projects
with build files. Platforms/infrastructure (Kubernetes, Docker, AWS, Terraform):
manifests, CLI sessions and IaC. Data stores (PostgreSQL, Kafka, Redis): schemas,
queries/commands and client code. Concept topics (System Design, Microservices,
Security): diagrams, worked designs and reference code in <EXAMPLE_LANGUAGE>.

RESUME PROTOCOL: if the user pastes this prompt plus a progress tracker into a new
chat and says "continue from here", skip Phases 0–2, re-read the tracker and carry on
with the next ⏳ page using the same inputs, template and running domain.

======================================================================
PHASE 5 — QUALITY RULES & SELF-CHECK
======================================================================
1. Accuracy over volume. Never invent APIs, flags, properties, annotations, error
   messages, benchmarks, CVEs, URLs or version numbers. If unsure, say so and mark it
   "Verify against the official docs".
2. Tone: a friendly mentor at L0–L2, precise and opinionated at L4–L5. Short
   paragraphs, active voice, no filler, no marketing language.
3. Use Material features where they help: admonitions (note, tip, info, warning,
   danger, example, question, success, abstract), content tabs, code annotations,
   Mermaid, tables, task lists, keyboard keys (++ctrl+c++), tooltips via the
   abbreviations file.
4. Code: idiomatic for the topic's current version, meaningful names, immutability by
   default, no secrets in code (use env vars/placeholders), deterministic output
   (no random values or real timestamps unless fixed or marked "sample").
5. Mermaid safety (diagrams that fail to parse break the page):
   - Wrap any label containing spaces, parentheses, brackets, colons, quotes, slashes
     or punctuation in double quotes: A["Order (pending)"].
   - Don't use reserved words (end, class, graph, style) as bare node ids.
   - In classDiagram, use only valid relation arrows (<|--, *--, o--, -->, ..>, ..|>)
     and put generics as Class~T~.
   - Keep one statement per line; no trailing semicolons inside labels.
6. Links: relative links between pages only to files that exist in the nav, so
   `mkdocs build --strict` passes. Every page links back to its prerequisites and
   forward to its "next" page.
7. Consistency: the same running-domain names (entities, services, tables) across
   every page and the capstone.
8. Self-check before finishing EACH page, and print a one-line result:
   (a) all mandatory sections present, (b) every syllabus subtopic for this page is
   covered with description + pros/cons where relevant, (c) exactly 3 examples, each
   with Scenario, Code, Run it, Output, What to notice, (d) code is complete and the
   output matches it, (e) the Expert Deep Dive has a war story, (f) Mermaid follows
   the safety rules, (g) links point to real pages. Fix any gap before moving on.
9. Final verification (after the last page): list every syllabus topic and subtopic
   with the page that covers it, and confirm none are missing; list any "Verify"
   flags so the user can check them; confirm the nav matches the files.

OUTPUT FORMAT
If OUTPUT_MODE = print-in-chat, output each file as:
### `path/to/file.ext`
```<lang>
...full file content...
```
If OUTPUT_MODE = write-to-disk, write the files directly and print only the file list, the
self-check line per page and the progress tracker.

Start now with PHASE 0: resolve the inputs.
````

---

## How to use this prompt

1. Copy the block above and fill in the **INPUT FIELDS** at the top. Only `TOPIC` is required; delete or leave any other `{{...}}` as it is to use the default.
2. Paste it into a new chat with a long-context model (Claude Opus or Sonnet recommended). If the model can write files (Claude Code, for example), run it inside an empty folder or a repo so it writes the site directly.
3. It prints the inputs it resolved and a full syllabus. Review the syllabus carefully, since this is where you make sure nothing is missing, then reply `approve`.
4. After the reference page, adjust the template if you want, then reply `continue` after each batch.
5. If the chat gets too long, start a new one, paste the same filled-in prompt plus the latest progress tracker, and say `continue from here`.
6. Run `mkdocs build --strict` locally to catch broken links and nav mistakes, then build and test the examples.

Don't edit the part below the input fields unless you want to change every future playbook. That's what keeps the theme, layout and page structure the same across topics.

## Example: filled-in input fields

**Spring Boot**

```text
TOPIC              = Spring Boot
VERSION            = 4.0
EXAMPLE_LANGUAGE   = Java 25
COMPANION_STACK    = Maven, PostgreSQL, Kafka, Redis, Docker, JUnit 5, Testcontainers
RUNNING_DOMAIN     = grocery delivery
SITE_NAME          = Spring Boot 4 Playbook
REPO_URL           = https://github.com/<you>/spring-boot-playbook
PRIMARY_COLOR      = green
ACCENT_COLOR       = light green
```

**Angular**

```text
TOPIC              = Angular
VERSION            = 20
EXAMPLE_LANGUAGE   = TypeScript 5
COMPANION_STACK    = Angular CLI, RxJS, Signals, NgRx, Vitest, Playwright
RUNNING_DOMAIN     = e-commerce
PRIMARY_COLOR      = red
ACCENT_COLOR       = pink
```

**Jakarta EE**

```text
TOPIC              = Jakarta EE
VERSION            = 11
EXAMPLE_LANGUAGE   = Java 21
COMPANION_STACK    = WildFly, Open Liberty, JPA/Hibernate, CDI, Arquillian, Docker
RUNNING_DOMAIN     = banking
DEPTH              = core
```

Every field you don't list keeps its default.

## Optional add-ons (append to the prompt if you want them)

- **Quiz mode:** "At the end of each page, add 5 MCQs with answers hidden in `??? question` blocks."
- **Second language for examples:** "Add a Kotlin (or Python, Go…) tab next to every primary-language code tab."
- **Explain in another language:** "After the analogy on every page, add a short `??? info "Explain in Hindi"` block."
- **Diagrams as images:** "Also export each Mermaid diagram as SVG under docs/assets/diagrams/."
- **Versioned docs:** "Add `mike` configuration so several versions of the playbook can be published side by side."
- **PDF export:** "Add the `mkdocs-with-pdf` plugin configuration so the playbook can be exported as a single PDF."
- **Verified outputs:** "Add a script in examples/ that runs every example and fails if its output differs from the Output block in the docs, and run it in CI."
