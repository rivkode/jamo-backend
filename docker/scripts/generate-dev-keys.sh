#!/usr/bin/env bash
# 로컬 dev 전용 RSA(PKCS#8) 키쌍 + refresh-token pepper 생성.
# 출력은 .env 에 그대로 붙여넣을 수 있는 형태 (이중 따옴표 multi-line — docker compose .env 파서가 지원).
# 운영 환경 사용 절대 금지.

set -euo pipefail

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl not found in PATH" >&2
  exit 1
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# JwkPemReader 가 PKCS#8 만 허용 (-----BEGIN PRIVATE KEY-----).
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/priv.pem" 2>/dev/null
openssl rsa -pubout -in "$tmp/priv.pem" -out "$tmp/pub.pem" 2>/dev/null

priv=$(cat "$tmp/priv.pem")
pub=$(cat "$tmp/pub.pem")
pepper=$(openssl rand -hex 32)

cat <<EOF
# === 아래 블록을 .env 끝에 붙여넣거나 기존 JWT_* 값을 교체하세요. ===
# Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ") (LOCAL DEV ONLY)
JWT_PRIVATE_KEY_PEM="${priv}"
JWT_PUBLIC_KEY_PEM="${pub}"
JWT_REFRESH_HASH_PEPPER=${pepper}
EOF
