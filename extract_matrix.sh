#!/bin/bash
# script to extract the Matrix Agent Protocol into a reusable template

TEMPLATE_DIR="../matrix-agent-template"

echo "Initializing Construct... Creating template directory at $TEMPLATE_DIR"
mkdir -p "$TEMPLATE_DIR"

# Core Agent Folders
echo "Copying agent workflows and commands..."
cp -R .agents "$TEMPLATE_DIR/"
cp -R .claude "$TEMPLATE_DIR/"

# Core Instructions
echo "Copying core instructions..."
cp AGENT_INFO.md "$TEMPLATE_DIR/"
cp CLAUDE.md "$TEMPLATE_DIR/"
cp .cursorrules "$TEMPLATE_DIR/"

# Remove project-specific files from the template's .agents folder
# (Assuming TASKS.md and tasks/ are project-specific)
rm -f "$TEMPLATE_DIR/.agents/TASKS.md"
rm -rf "$TEMPLATE_DIR/.agents/tasks/"*

# Create empty template files
touch "$TEMPLATE_DIR/BACKLOG.md"
echo "# Project Backlog" > "$TEMPLATE_DIR/BACKLOG.md"

touch "$TEMPLATE_DIR/CHANGELOG.md"
echo "# Changelog" > "$TEMPLATE_DIR/CHANGELOG.md"

mkdir -p "$TEMPLATE_DIR/.agents/tasks"
touch "$TEMPLATE_DIR/.agents/TASKS.md"
echo "| ID | Task | Status |" > "$TEMPLATE_DIR/.agents/TASKS.md"

echo "Extraction complete. The Matrix Protocol is now ready to be deployed to a new repository from $TEMPLATE_DIR."
echo "To use: copy the contents of $TEMPLATE_DIR into the root of any new project."
