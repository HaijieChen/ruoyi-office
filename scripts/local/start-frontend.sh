#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "${script_dir}/../.." && pwd)
frontend_dir="${repo_dir}/ruoyi-office-vben"
frontend_host=${FRONTEND_HOST:-127.0.0.1}
frontend_port=${FRONTEND_PORT:-5666}

cd "${frontend_dir}"
exec npm exec --yes --package=pnpm@10.28.2 -- pnpm \
  --filter @vben/web-antd exec vite \
  --mode development \
  --host "${frontend_host}" \
  --port "${frontend_port}" \
  --strictPort
