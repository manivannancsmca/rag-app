#!/usr/bin/env bash
set -euo pipefail

OLLAMA_HOST="${OLLAMA_HOST:-http://localhost:11434}"

echo "⏳ Waiting for Ollama to become ready..."
until curl -sf "${OLLAMA_HOST}/api/tags" > /dev/null 2>&1; do
    sleep 2
done
echo "✅ Ollama is ready."

echo "📦 Pulling llama3.2:1b (chat model)..."
curl -s "${OLLAMA_HOST}/api/pull" -d '{"name":"llama3.2:1b"}'

echo ""
echo "📦 Pulling nomic-embed-text (embedding model)..."
curl -s "${OLLAMA_HOST}/api/pull" -d '{"name":"nomic-embed-text"}'

echo ""
echo "✅ All models pulled."