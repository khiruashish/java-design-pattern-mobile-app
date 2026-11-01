#!/usr/bin/env python3
"""Turn the MkDocs playbook into content the Flutter app can show offline.

Reads mkdocs.yml (for the nav order) and docs/, resolves the `--8<--` snippet
includes (so example code and outputs are the real files), and converts the
Material for MkDocs extensions the app's Markdown renderer doesn't know:
admonitions, content tabs, code annotations, attribute lists, badges.

Writes app/assets/content/index.json and one .md file per page.
Run from the repository root:  python3 tools/build_app_content.py
"""
import json
import os
import re
import shutil
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
OUT = os.path.join(ROOT, "app", "assets", "content")

LEVELS = {
    "l0-orientation": ("L0", "Orientation", "Day 0"),
    "l1-foundations": ("L1", "Foundations", "0–1 yr"),
    "l2-practitioner": ("L2", "Practitioner", "1–3 yrs"),
    "l3-professional": ("L3", "Professional", "3–6 yrs"),
    "l4-senior": ("L4", "Senior / Lead", "6–10 yrs"),
    "l5-staff-principal": ("L5", "Staff / Principal", "10–15+ yrs"),
}
SECTIONS = {"reference": "Reference", "projects": "Projects", "interview": "Interview"}
STUB_MARKER = '!!! info "This page is being written"'


class _IgnoreTags(yaml.SafeLoader):
    pass


_IgnoreTags.add_multi_constructor("", lambda loader, suffix, node: None)


def nav_pages():
    with open(os.path.join(ROOT, "mkdocs.yml")) as f:
        config = yaml.load(f, Loader=_IgnoreTags)

    def walk(items):
        for item in items:
            if isinstance(item, str):
                yield None, item
            elif isinstance(item, dict):
                for title, value in item.items():
                    if isinstance(value, str):
                        yield title, value
                    else:
                        yield from walk(value)

    return list(walk(config["nav"]))


def resolve_snippets(text):
    def repl(m):
        indent, path = m.group(1), m.group(2)
        with open(os.path.join(ROOT, path)) as f:
            body = f.read().rstrip("\n")
        return "\n".join(indent + line if line else line for line in body.split("\n"))

    return re.sub(r'^([ \t]*)--8<-- "([^"]+)"[ \t]*$', repl, text, flags=re.M)


def dedent_block(lines, start):
    """Collect lines indented by 4 spaces (or blank) after `start`; return (block, next_index)."""
    block, i = [], start
    while i < len(lines) and (lines[i].startswith("    ") or lines[i].strip() == ""):
        block.append(lines[i][4:] if lines[i].startswith("    ") else "")
        i += 1
    while block and block[-1] == "":
        block.pop()
    return block, i


ICONS = {"abstract": "📌", "tip": "💡", "info": "ℹ️", "warning": "⚠️", "danger": "🚨", "example": "📖",
         "success": "✅", "question": "❓", "note": "📝"}


def convert(text):
    text = re.sub(r"\A---\n.*?\n---\n", "", text, flags=re.S)           # front matter
    text = resolve_snippets(text)
    text = re.sub(r"<span[^>]*>(.*?)</span>", r"\1", text)               # badges / pills
    text = re.sub(r"\{ ?[.#][^}]*\}", "", text)                          # attr_list
    text = re.sub(r":(material|octicons|fontawesome)-[a-z0-9-]+:", "", text)
    text = re.sub(r"\+\+([a-z]+)\+([a-z])\+\+", lambda m: f"{m.group(1).title()}+{m.group(2).upper()}", text)
    text = re.sub(r"[ \t]*(//|#|<!--)[ \t]*\(\d+\)![ \t]*(-->)?", "", text)  # code annotation markers
    text = re.sub(r'(```[a-z]*) title="([^"]+)"', r"**\2**\n\n\1", text)   # code block titles

    lines, out, i = text.split("\n"), [], 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r'^(\s*)(!!!|\?\?\?\+?) (\w+)(?: "((?:[^"\\]|\\.)*)")?\s*$', line)
        t = re.match(r'^(\s*)=== "([^"]+)"\s*$', line)
        if m:                                                              # admonition -> quote
            indent, kind, title = m.group(1), m.group(3), m.group(4)
            title = re.sub(r'\\(.)', r'\1', title) if title else title    # titles may escape quotes
            sub = [l[len(indent):] if l.startswith(indent) else l for l in lines[i + 1:]]
            block, used = dedent_block(sub, 0)
            heading = f"{ICONS.get(kind, '📝')} **{title or kind.title()}**  "   # hard line break
            quoted = [heading] + convert_inner(block)
            out += [indent + "> " + l if l else indent + ">" for l in quoted]
            out.append("")
            i += 1 + used
        elif t:                                                            # content tab -> bold label
            indent, label = t.group(1), t.group(2)
            sub = [l[len(indent):] if l.startswith(indent) else l for l in lines[i + 1:]]
            block, used = dedent_block(sub, 0)
            out += [indent + f"**▸ {label}**", ""] + [indent + l if l else l for l in convert_inner(block)] + [""]
            i += 1 + used
        else:
            out.append(line)
            i += 1
    text = "\n".join(out)
    text = re.sub(r"^```mermaid\n.*?^```$", "> 📊 *Diagram: open this page on the website to see it drawn.*", text, flags=re.S | re.M)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip() + "\n"


def convert_inner(block):
    return convert("\n".join(block)).rstrip("\n").split("\n")


def first_heading(text):
    m = re.search(r"^# (.+)$", text, flags=re.M)
    return m.group(1).strip() if m else None


def tldr(text):
    m = re.search(r'!!! abstract "TL;DR"\n((?:    .*\n?)+)', text)
    return " ".join(l.strip() for l in m.group(1).split("\n")).strip() if m else ""


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    pages, groups = [], {}
    for nav_title, path in nav_pages():
        with open(os.path.join(DOCS, path)) as f:
            raw = f.read()
        title = first_heading(raw) or nav_title or path
        folder = path.split("/")[0] if "/" in path else ""
        minutes = re.search(r"⏱ (\d+) min read", raw)
        tags = re.search(r"\Atags: \[([^\]]*)\]", raw.split("---\n")[1] if raw.startswith("---") else "")
        page = {
            "id": path[:-3].replace("/", "__"),
            "path": path,
            "title": title,
            "summary": tldr(raw),
            "minutes": int(minutes.group(1)) if minutes else None,
            "written": STUB_MARKER not in raw,
            "isIndex": path.endswith("index.md"),
            "tags": [t.strip() for t in tags.group(1).split(",")] if tags else [],
            "group": folder,
        }
        with open(os.path.join(OUT, page["id"] + ".md"), "w") as f:
            f.write(convert(raw))
        pages.append(page)
        if folder in LEVELS or folder in SECTIONS:
            groups.setdefault(folder, []).append(page["id"])

    index = {
        "levels": [{"id": k, "code": v[0], "name": v[1], "experience": v[2], "pages": groups.get(k, [])}
                   for k, v in LEVELS.items()],
        "sections": [{"id": k, "name": v, "pages": groups.get(k, [])} for k, v in SECTIONS.items()],
        "pages": pages,
    }
    with open(os.path.join(OUT, "index.json"), "w") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)
    written = sum(1 for p in pages if p["written"] and not p["isIndex"] and p["group"] in LEVELS)
    total = sum(1 for p in pages if not p["isIndex"] and p["group"] in LEVELS)
    print(f"{len(pages)} pages exported to {os.path.relpath(OUT, ROOT)} ({written}/{total} topic pages written)")


if __name__ == "__main__":
    sys.exit(main())
