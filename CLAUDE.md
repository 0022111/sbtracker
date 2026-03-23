# sbtracker - Claude Code Notes

## Git Push Workaround

**The platform's git proxy and GitHub MCP integration do NOT have write access to this repo.**

- `git push` via the local proxy (`127.0.0.1:*`) will fail with `403 Permission denied`
- `mcp__github__create_branch` / `mcp__github__push_files` will fail with `403 Resource not accessible by integration`

**To push branches or create refs, use the GitHub REST API directly with the PAT:**

```bash
# Create a branch
curl -s -X POST \
  -H "Authorization: token <PAT>" \
  -H "Content-Type: application/json" \
  -d "{\"ref\":\"refs/heads/<branch-name>\",\"sha\":\"<sha>\"}" \
  https://api.github.com/repos/0022111/sbtracker/git/refs

# Update a branch (force push)
curl -s -X PATCH \
  -H "Authorization: token <PAT>" \
  -H "Content-Type: application/json" \
  -d "{\"sha\":\"<sha>\",\"force\":true}" \
  https://api.github.com/repos/0022111/sbtracker/git/refs/heads/<branch-name>
```

The PAT should be provided by the user. After pushing via API, run:
```bash
git fetch origin <branch-name>
git branch -u origin/<branch-name> <branch-name>
```

To sync the local tracking branch with the remote.
