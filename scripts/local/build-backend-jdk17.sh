#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "${script_dir}/../.." && pwd)
maven_image='maven:3.9-eclipse-temurin-17'
maven_cache='/Users/chenhaijie/.m2'
jar_path="${repo_dir}/yudao-server/target/yudao-server.jar"

docker image inspect "${maven_image}" >/dev/null
docker run --rm "${maven_image}" java -version 2>&1 | grep -q 'version "17\.'

docker run --rm \
  -v "${repo_dir}:/workspace" \
  -v "${maven_cache}:/root/.m2" \
  -w /workspace \
  "${maven_image}" \
  mvn -pl yudao-server -am clean package -DskipTests

test -s "${jar_path}"
jar tf "${jar_path}" | grep -q 'BOOT-INF/lib/yudao-module-crm-server-.*\.jar'
jar tf "${jar_path}" | grep -q 'BOOT-INF/lib/yudao-module-erp-server-.*\.jar'

stat -f 'built jar: %z bytes %N' "${jar_path}"
