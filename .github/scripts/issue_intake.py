#!/usr/bin/env python3
"""
GitHub Issue Intake
Converts a labelled GitHub issue into a BACKLOG.md row and posts a comment.

Required env vars:
  ANTHROPIC_API_KEY
  ISSUE_NUMBER, ISSUE_TITLE, ISSUE_BODY, ISSUE_LABELS (JSON array of strings)
  GH_TOKEN  (for posting the comment back)
  GITHUB_REPOSITORY  (e.g. "0022111/sbtracker", set automatically by Actions)
"""

import json
import os
import re
import sys
import urllib.request
from pathlib import Path

import anthropic

REPO_ROOT = Path(__file__).resolve().parent.parent.parent

CATEGORY_SECTIONS = [
    "## Core Systems",
    "## Data Insight",
    "## Device Management",
    "## UI & Visualization",
    "## Quality & Infra",
    "## Dreams & Wishlist",
    "## Bugs",
]


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
        header_name = section_header.replace("## ", "")
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
        l = lines[i]
        if l.startswith("|"):
            last_row_line = i
        elif l.startswith("## ") or l.strip() == "---":
            break  # hit next section

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
    project = project_path.read_text()[:3000]  # cap context size

    # Skip if already processed
    if already_ingested(backlog, issue_number):
        print(f"Issue #{issue_number} already in BACKLOG.md — skipping.")
        sys.exit(0)

    # Determine type
    is_bug = "bug" in labels_lower
    is_dream = "dream" in labels_lower or "wishlist" in labels_lower

    # Determine priority from label, fallback to defaults
    if "p0" in labels_lower:
        priority = "P0"
    elif "p2" in labels_lower or is_dream:
        priority = "P2"
    else:
        priority = "P1"

    max_f, max_b = get_highest_ids(backlog)
    next_id = f"B-{max_b + 1:03d}" if is_bug else f"F-{max_f + 1:03d}"

    client = anthropic.Anthropic()

    type_hint = (
        "BUG — add to the Bugs table"
        if is_bug
        else (
            "DREAM / WISHLIST — add to the Dreams & Wishlist section (P2, speculative)"
            if is_dream
            else "FEATURE or IMPROVEMENT — add to the most relevant feature category table"
        )
    )

    row_format = (
        f"| {next_id} | `planned` | {priority} | <one-sentence description> (#{{issue_number}}) |"
        if is_bug
        else f"| {next_id} | `planned` | <Short Title> | <one-sentence description> (#{{issue_number}}) | <2-3 bullet acceptance criteria> |"
    )

    prompt = f"""You are the Intake Agent for SBTracker, an Android BLE app that tracks vaporizer sessions.

BACKLOG.md (current):
<backlog>
{backlog}
</backlog>

PROJECT.md excerpt (architecture context):
<project>
{project}
</project>

New GitHub issue:
  Number : #{issue_number}
  Title  : {issue_title}
  Labels : {", ".join(labels) or "none"}
  Body   : {issue_body or "(no body)"}

Classification: {type_hint}
Assigned ID: {next_id}
Priority: {priority}

Write exactly ONE table row in this format:
{row_format.format(issue_number=issue_number)}

Also output on a second line the exact section header to insert it under, chosen from:
  ## Core Systems
  ## Data Insight
  ## Device Management
  ## UI & Visualization
  ## Quality & Infra
  ## Dreams & Wishlist
  ## Bugs

Output format (two lines, nothing else):
ROW: <the table row>
SECTION: <the section header>"""

    msg = client.messages.create(
        model="claude-opus-4-6",
        max_tokens=300,
        messages=[{"role": "user", "content": prompt}],
    )
    response = msg.content[0].text.strip()
    print(f"Claude response:\n{response}")

    # Parse output
    row = next_section = None
    for line in response.splitlines():
        if line.startswith("ROW:"):
            row = line[4:].strip()
        elif line.startswith("SECTION:"):
            next_section = line[8:].strip()

    if not row or not next_section:
        print("Could not parse Claude output — aborting.")
        print(response)
        sys.exit(1)

    # Insert into BACKLOG.md
    updated = insert_row_into_section(backlog, next_section, row)
    backlog_path.write_text(updated)
    print(f"Inserted {next_id} into '{next_section}'")

    # Post comment on the issue
    comment = (
        f"**Intake complete** — assigned **{next_id}** and added to `BACKLOG.md` "
        f"under _{next_section.lstrip('# ')}_.\n\n"
        f"```\n{row}\n```\n\n"
        f"Run `/plan-feature {next_id}` to decompose into tasks when ready."
    )
    post_github_comment(issue_number, comment)


if __name__ == "__main__":
    main()
