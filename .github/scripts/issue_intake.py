#!/usr/bin/env python3
"""
GitHub Issue Intake
Converts a labelled GitHub issue into a BACKLOG.md row and posts a comment.

Required env vars:
  ANTHROPIC_API_KEY
  ISSUE_NUMBER, ISSUE_TITLE, ISSUE_BODY, ISSUE_LABELS (JSON array of strings)
  GH_TOKEN  (for posting the comment back)
  GITHUB_REPOSITORY  (e.g. "0022111/sbtracker", set automatically by Actions)

Priority is auto-determined by Claude unless overridden by a p0/p1/p2 label.
Triggering labels: bug, enhancement
"""

import json
import os
import re
import sys
import urllib.request
from pathlib import Path

import anthropic

REPO_ROOT = Path(__file__).resolve().parent.parent.parent


def get_highest_ids(backlog: str) -> tuple[int, int]:
    f_ids = [int(m) for m in re.findall(r"\| F-(\d+)", backlog)]
    b_ids = [int(m) for m in re.findall(r"\| B-(\d+)", backlog)]
    return max(f_ids, default=0), max(b_ids, default=0)


def already_ingested(backlog: str, issue_number: int) -> bool:
    return f"(#{issue_number})" in backlog


def insert_row_into_section(backlog: str, section_header: str, row: str) -> str:
    """Append a table row at the end of the named section's table."""
    lines = backlog.splitlines()
    section_line = next(
        (i for i, l in enumerate(lines) if l.strip().startswith(section_header)),
        None,
    )
    if section_line is None:
        # Section doesn't exist — create it before the Notes section
        notes_line = next(
            (i for i, l in enumerate(lines) if l.strip().startswith("## Notes")),
            len(lines),
        )
        is_bug = "Bugs" in section_header
        new_section = [
            "",
            "---",
            "",
            section_header,
            "",
            "| ID | Status | Priority | Description |" if is_bug
            else "| ID | Status | Feature | Description | Acceptance Criteria |",
            "|---|---|---|---|" if is_bug else "|---|---|---|---|---|",
            row,
        ]
        lines[notes_line:notes_line] = new_section
        return "\n".join(lines)

    # Find the last table row in this section
    last_row_line = section_line
    for i in range(section_line + 1, len(lines)):
        if lines[i].startswith("|"):
            last_row_line = i
        elif lines[i].startswith("## ") or lines[i].strip() == "---":
            break

    lines.insert(last_row_line + 1, row)
    return "\n".join(lines)


def post_github_comment(issue_number: int, body: str) -> None:
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    if not token or not repo:
        print("No GH_TOKEN or GITHUB_REPOSITORY — skipping comment.")
        return
    url = f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments"
    data = json.dumps({"body": body}).encode()
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as resp:
            print(f"Comment posted: {resp.status}")
    except Exception as e:
        print(f"Failed to post comment: {e}")


def main() -> None:
    issue_number = int(os.environ["ISSUE_NUMBER"])
    issue_title = os.environ["ISSUE_TITLE"]
    issue_body = os.environ.get("ISSUE_BODY") or ""
    labels: list[str] = json.loads(os.environ.get("ISSUE_LABELS", "[]"))
    labels_lower = [l.lower() for l in labels]

    backlog_path = REPO_ROOT / "BACKLOG.md"
    project_path = REPO_ROOT / "PROJECT.md"
    backlog = backlog_path.read_text()
    project = project_path.read_text()[:3000]

    if already_ingested(backlog, issue_number):
        print(f"Issue #{issue_number} already in BACKLOG.md — skipping.")
        sys.exit(0)

    is_bug = "bug" in labels_lower

    # Priority: explicit label wins; otherwise Claude decides from content
    forced_priority = None
    if "p0" in labels_lower:
        forced_priority = "P0"
    elif "p1" in labels_lower:
        forced_priority = "P1"
    elif "p2" in labels_lower:
        forced_priority = "P2"

    max_f, max_b = get_highest_ids(backlog)
    next_id = f"B-{max_b + 1:03d}" if is_bug else f"F-{max_f + 1:03d}"

    client = anthropic.Anthropic()

    priority_instruction = (
        f"Priority is forced to **{forced_priority}** by label — use it exactly."
        if forced_priority
        else (
            "Priority is NOT specified — you must infer it from the content:\n"
            "  P0 = blocking / core system / crashes\n"
            "  P1 = meaningful user-facing improvement\n"
            "  P2 = nice-to-have / low urgency\n"
            "Output your chosen priority on the PRIORITY line."
        )
    )

    if is_bug:
        row_template = f"| {next_id} | `planned` | <PRIORITY> | <one-sentence description> (#{issue_number}) |"
        section_choices = "  ## Bugs"
    else:
        row_template = f"| {next_id} | `planned` | <Short Title> | <one-sentence description> (#{issue_number}) | <2-3 bullet acceptance criteria> |"
        section_choices = (
            "  ## Core Systems\n"
            "  ## Data Insight\n"
            "  ## Device Management\n"
            "  ## UI & Visualization\n"
            "  ## Quality & Infra"
        )

    prompt = f"""You are the Intake Agent for SBTracker, an Android BLE app that tracks vaporizer sessions.

BACKLOG.md (current):
<backlog>
{backlog}
</backlog>

PROJECT.md excerpt:
<project>
{project}
</project>

New GitHub issue:
  Number : #{issue_number}
  Title  : {issue_title}
  Labels : {", ".join(labels) or "none"}
  Body   : {issue_body or "(no body)"}

Type: {"BUG" if is_bug else "FEATURE / ENHANCEMENT"}
Assigned ID: {next_id}
{priority_instruction}

Write exactly ONE table row:
{row_template}

Output exactly three lines, nothing else:
ROW: <the completed table row>
SECTION: <chosen from:{" ## Bugs" if is_bug else chr(10) + section_choices}>
PRIORITY: <P0|P1|P2>"""

    msg = client.messages.create(
        model="claude-opus-4-6",
        max_tokens=300,
        messages=[{"role": "user", "content": prompt}],
    )
    response = msg.content[0].text.strip()
    print(f"Claude response:\n{response}")

    row = next_section = priority = None
    for line in response.splitlines():
        if line.startswith("ROW:"):
            row = line[4:].strip()
        elif line.startswith("SECTION:"):
            next_section = line[8:].strip()
        elif line.startswith("PRIORITY:"):
            priority = line[9:].strip()

    if not row or not next_section or not priority:
        print("Could not parse Claude output — aborting.")
        print(response)
        sys.exit(1)

    # Substitute <PRIORITY> placeholder in row (bugs only)
    row = row.replace("<PRIORITY>", priority)

    updated = insert_row_into_section(backlog, next_section, row)
    backlog_path.write_text(updated)
    print(f"Inserted {next_id} ({priority}) into '{next_section}'")

    comment = (
        f"**Intake complete** — assigned **{next_id}** ({priority}), "
        f"added to `BACKLOG.md` under _{next_section.lstrip('# ')}_.\n\n"
        f"```\n{row}\n```\n\n"
        f"Run `/plan-feature {next_id}` to decompose into tasks when ready."
    )
    post_github_comment(issue_number, comment)


if __name__ == "__main__":
    main()
