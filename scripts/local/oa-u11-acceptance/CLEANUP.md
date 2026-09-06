# U11 iso cleanup (detached Docker; survives agent session)

Do **not** touch 48080 / 33061 / 6379 / 5666 / U9 13306 / 5687 / 其它会话容器（含 seal-s7-e2e-*、royi-oa-u9u10-iso-mysql）。本文件只说明如何拆 U11 iso；**写入时未执行清理，验收期间保留隔离服务。**

当前 API **不要**再 bind 共享 `yudao-server/target/yudao-server.jar`。验收包已复制为只读不可变：

- jar SHA `8def53f0a335a454228c7f661c033a0d1a497cad58d28db87839bc06d61c4102`
- host `/tmp/royi-oa-u11-artifacts/8def53f0a335a454228c7f661c033a0d1a497cad58d28db87839bc06d61c4102.jar` → 容器 `/yudao-server/app.jar` ro
- 配置仍 bind `scripts/local/oa-u11-acceptance/application-u11iso.yaml`
- 容器 `oa-u11-iso-api` `59283297bafaf70618b7dba3370b081600824e251a93a883277472301823d5bb` network `oa-u11-iso-int` only、`--ip 172.31.0.4`、无 publish

```sh
docker rm -f oa-u11-iso-api oa-u11-iso-proxy oa-u11-iso-mysql oa-u11-iso-redis
docker network rm oa-u11-iso-int oa-u11-iso-net
docker volume rm oa-u11-iso-mysql oa-u11-iso-redis
# optional: rm -rf .tmp-u11-m2
# optional: leave /tmp/royi-oa-u11-artifacts (keeps 8def copy + Nov/Dec JSON)
```

IDs:

- user bridge `oa-u11-iso-net` `72ded60d124076fe72d236b692514250e1cc0b7efb9c304eb664fcdc4ddbec89` internal=false
- internal `oa-u11-iso-int` `330bfe64df71eac0997d09998cf4f96df71f656364dbdf628925722d19486303` internal=true
- mysql `oa-u11-iso-mysql` `c834a590490d63c2e008d66d22cb936e1800f33818df8dda115ac3fd5c8a39f5` nets=int+bridge publish `127.0.0.1:33062`
- redis `oa-u11-iso-redis` `2087b4fefd4837e151066e0ff0a74286c0c9c4d404b602ce299779fe37430f39` nets=int+bridge publish `127.0.0.1:6381`
- api `oa-u11-iso-api` `59283297bafaf70618b7dba3370b081600824e251a93a883277472301823d5bb` **internal only, no publish**（旧 id `faae061f` / `8d10f786` 已替换）
- proxy `oa-u11-iso-proxy` `1db0cb976259bb57e6d31ece6f925adecacd39e40c83885e0e5d82a19ca68c3c` nginx:stable-alpine publish `127.0.0.1:48081` → `oa-u11-iso-api:48081`

Vite (parent): `VITE_PROXY_TARGET=http://127.0.0.1:48081/admin-api` then restart bash-3. Do not point at 48080.
