#!/usr/bin/env bash
# Run once with repository Actions-secrets write permission. No new dependencies.
# Requires installed OpenSSL, Python 3, gh. Never prints private key/password.
set -euo pipefail
repo=WaquarShaikh5555/eternalz-forge
# Refuse to generate a new identity if the persistent one already exists.
existing=$(gh secret list --repo "$repo")
if grep -q '^FORGE_ANDROID_SIGNING[[:space:]]' <<< "$existing"; then
  echo 'Signing identity already exists. Refusing to rotate it and break installed updates.'
  exit 1
fi
umask 077
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
openssl rand -hex 32 > "$work/password"
openssl req -x509 -newkey rsa:4096 -sha256 -days 10000 -nodes \
  -keyout "$work/key.pem" -out "$work/cert.pem" \
  -subj '/CN=Eternalz Forge Android Distribution/' 2>/dev/null
openssl pkcs12 -export -name forge -inkey "$work/key.pem" -in "$work/cert.pem" \
  -out "$work/signing.p12" -passout "file:$work/password"
python3 - "$work" <<'PY'
import base64,json,pathlib,sys
p=pathlib.Path(sys.argv[1])
(p/'secret.json').write_text(json.dumps({'keystore':base64.b64encode((p/'signing.p12').read_bytes()).decode(),'password':(p/'password').read_text().strip()}))
PY
gh secret set FORGE_ANDROID_SIGNING --repo "$repo" < "$work/secret.json"
echo 'Permanent signing identity stored in repository Actions secret FORGE_ANDROID_SIGNING.'
echo 'Do not delete or replace that secret. Its loss prevents future compatible updates.'
openssl x509 -in "$work/cert.pem" -noout -fingerprint -sha256
