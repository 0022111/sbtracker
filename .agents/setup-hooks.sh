#!/bin/bash
# setup-hooks.sh — Initialize git hooks to enforce workflow rules
# Run: bash .agents/setup-hooks.sh

set -e

REPO_ROOT=$(git rev-parse --show-toplevel)
HOOKS_DIR="$REPO_ROOT/.git/hooks"

echo "🔧 Setting up git hooks for sbtracker workflow enforcement..."
echo ""

# Ensure .git/hooks exists
mkdir -p "$HOOKS_DIR"

# Make pre-push hook executable
if [ -f "$HOOKS_DIR/pre-push" ]; then
  chmod +x "$HOOKS_DIR/pre-push"
  echo "✓ pre-push hook enabled (prevents accidental pushes to dev/main)"
else
  echo "⚠️  pre-push hook not found at $HOOKS_DIR/pre-push"
  echo "   Make sure you're in the sbtracker repo root."
  exit 1
fi

echo ""
echo "✅ Hooks configured successfully!"
echo ""
echo "What the pre-push hook does:"
echo "  • Blocks direct pushes to 'dev' or 'main' branches"
echo "  • Allows pushes to feature branches (claude/*)"
echo "  • Provides helpful error messages with correct workflow steps"
echo ""
echo "If you need to push a meta-file update directly to dev:"
echo "  1. Fetch dev: git fetch origin dev"
echo "  2. Create isolated branch: git checkout -b meta-update origin/dev"
echo "  3. Edit ONLY meta-files (TASKS.md, BACKLOG.md, etc.)"
echo "  4. Commit: git commit -m 'meta: update'"
echo "  5. Push: git push origin HEAD:dev"
echo "  6. The pre-push hook will allow this because HEAD doesn't match 'dev' locally"
echo ""
