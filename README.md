# Java Design Patterns Playbook

A MkDocs (Material) site that teaches Java design patterns from Day 0 to staff/principal level: every GoF pattern plus modern Java, enterprise, concurrency, architecture, DDD, microservice and UI/mobile patterns. Every page follows one template with three runnable Java 21 examples, pros and cons, pitfalls, an expert deep dive and interview questions.

It was generated with [PLAYBOOK_MASTER_PROMPT.md](PLAYBOOK_MASTER_PROMPT.md). The approved topic list is in [SYLLABUS.md](SYLLABUS.md).

## Mobile app

The [`app/`](app/) folder is a Flutter app (Android, iOS, web) that shows the same pages offline, with search, bookmarks and reading progress. See [app/README.md](app/README.md).

## Run the site locally

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
mkdocs serve          # http://127.0.0.1:8000
mkdocs build --strict
```

## Run the examples

Examples are single-file Java 21 programs in `examples/`, included into the pages so the docs and code never drift apart.

```bash
java examples/l1-foundations/oop-refresher/PaymentDemo.java
./examples/run-all.sh   # runs every *Demo.java and diffs it with its .expected.txt
```

Set `JUNIT_JAR` to a `junit-platform-console-standalone` jar to run the `*Test.java` files as well. CI does both on every pull request.

## Publish

Pushing to `main` builds the site and deploys it with `mkdocs gh-deploy`. Then turn on GitHub Pages: Settings → Pages → Deploy from a branch → `gh-pages`.

## Contribute

Add or edit pages under `docs/`, keep the page template from the master prompt, put example code in `examples/<level>/<topic>/` with a matching `.expected.txt`, and make sure `mkdocs build --strict` and `./examples/run-all.sh` pass.
