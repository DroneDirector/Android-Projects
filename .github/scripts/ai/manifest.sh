set -e
# The list of downloadable app files with size and checksum; the app compares it after an update.
gh api repos/$GITHUB_REPOSITORY/releases/tags/ai-models-v1 --jq '[.assets[] | select(.name != "manifest.json") | {key: .name, value: {size: .size, sha256: ((.digest // "") | sub("^sha256:"; "")), updated: .updated_at}}] | from_entries' > out/manifest.json
head -c 600 out/manifest.json; echo
gh release upload ai-models-v1 out/manifest.json -R "$GITHUB_REPOSITORY" --clobber
