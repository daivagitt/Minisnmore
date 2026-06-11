#!/usr/bin/env bash
# Verify Cloudflare R2 credentials from UAT env can read/write the configured bucket.
set -euo pipefail

ENV_FILE="${1:-/etc/mini-and-more/uat.env}"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${S3_ENDPOINT:?S3_ENDPOINT missing}"
: "${S3_BUCKET:?S3_BUCKET missing}"
: "${S3_ACCESS_KEY:?S3_ACCESS_KEY missing}"
: "${S3_SECRET_KEY:?S3_SECRET_KEY missing}"
: "${S3_REGION:=auto}"

python3 <<'PY'
import os
import sys
import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

endpoint = os.environ["S3_ENDPOINT"]
bucket = os.environ["S3_BUCKET"]
region = os.environ.get("S3_REGION", "auto")
prefix = os.environ.get("S3_KEY_PREFIX", "uat").strip("/")
test_key = f"{prefix}/_uat-verify.txt" if prefix else "_uat-verify.txt"

cfg = Config(signature_version="s3v4", s3={"addressing_style": "path"})
s3 = boto3.client(
    "s3",
    endpoint_url=endpoint,
    aws_access_key_id=os.environ["S3_ACCESS_KEY"],
    aws_secret_access_key=os.environ["S3_SECRET_KEY"],
    region_name=region,
    config=cfg,
)

print(f"Endpoint: {endpoint}")
print(f"Bucket:   {bucket}")
print(f"Test key: {test_key}")

failed = False
for name, fn in [
    ("head_bucket", lambda: s3.head_bucket(Bucket=bucket)),
    ("put_object", lambda: s3.put_object(Bucket=bucket, Key=test_key, Body=b"uat-verify", ContentType="text/plain")),
    ("get_object", lambda: s3.get_object(Bucket=bucket, Key=test_key)),
    ("delete_object", lambda: s3.delete_object(Bucket=bucket, Key=test_key)),
]:
    try:
        fn()
        print(f"[ok] {name}")
    except ClientError as exc:
        failed = True
        err = exc.response.get("Error", {})
        print(f"[fail] {name}: {err.get('Code', 'ClientError')} — {err.get('Message', exc)}")
    except Exception as exc:
        failed = True
        print(f"[fail] {name}: {type(exc).__name__} — {exc}")

if failed:
    print()
    print("R2 credentials cannot access this bucket. In Cloudflare dashboard:")
    print("  R2 → Manage R2 API Tokens → Create API token")
    print("  Permissions: Object Read & Write (scoped to bucket miniandmore)")
    print("Then update S3_ACCESS_KEY / S3_SECRET_KEY in uat.env and restart mini-uat-ms-catalog-core.")
    sys.exit(1)

print()
print("R2 storage credentials are valid.")
PY
