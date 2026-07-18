#!/bin/sh
set -eu

mysql_container='ruoyi-office-mysql'
redis_container='ruoyi-office-redis'
backend_container='ruoyi-office-backend'
secret_path='/run/secrets/bpm.form-data-source.jdbc.password'
temporary_dir=$(mktemp -d /private/tmp/ruoyi-office-secret.XXXXXX)
temporary_secret="${temporary_dir}/bpm.form-data-source.jdbc.password"

cleanup() {
  if [ -f "${temporary_secret}" ]; then
    chmod 600 "${temporary_secret}" || true
    rm -f "${temporary_secret}"
  fi
  rmdir "${temporary_dir}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

chmod 700 "${temporary_dir}"
umask 077
openssl rand -hex 32 >"${temporary_secret}"

docker start "${mysql_container}" "${redis_container}" >/dev/null

new_password=$(tr -d '\n' <"${temporary_secret}")
printf "ALTER USER 'ruoyi_form_reader'@'%%' IDENTIFIED BY '%s'; FLUSH PRIVILEGES;\n" "${new_password}" \
  | docker exec -i "${mysql_container}" sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' >/dev/null
unset new_password

docker restart "${backend_container}" >/dev/null
docker exec -i "${backend_container}" sh -lc \
  'umask 077; cat > /run/secrets/bpm.form-data-source.jdbc.password' \
  <"${temporary_secret}"
docker exec "${backend_container}" chmod 400 "${secret_path}"

attempt=0
while [ "${attempt}" -lt 90 ]; do
  if curl -fsS http://127.0.0.1:48080/actuator/health >/dev/null 2>&1; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 1
done

if [ "${attempt}" -ge 90 ]; then
  echo 'backend health check timed out' >&2
  exit 1
fi

docker exec "${backend_container}" stat -c 'runtime jar: %s bytes %n' /app.jar
docker exec "${backend_container}" sh -lc \
  'if ls -l /proc/1/fd 2>/dev/null | grep -q "app.jar (deleted)"; then exit 1; fi'
curl -fsS http://127.0.0.1:48080/actuator/health
