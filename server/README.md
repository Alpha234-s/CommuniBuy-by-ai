# 后端运行与验收

Java 21、Gradle Wrapper 8.14.3、Spring Boot 3.5.16、MySQL 8.4.6。没有 H2 替代测试。

## 本地运行（隔离数据库）

在 `server/` 执行，需要 JDK 21、Docker Compose。复制 `.env.example` 为 `.env`，为数据库用户、root、JWT 分别生成随机秘密。没有默认管理员，也不会连接或自动创建共享数据库。

```bash
cp .env.example .env
python3 -c "import secrets,base64; print(secrets.token_urlsafe(32)); print(secrets.token_urlsafe(32)); print(base64.b64encode(secrets.token_bytes(48)).decode())"
# 依次填入 DB_PASSWORD、MYSQL_ROOT_PASSWORD、JWT_SECRET。不要提交 .env。
docker compose up -d --wait
```

Compose 自动读取 `.env`；Java/Gradle 不会自动读取它。使用下面一项将变量载入当前终端（仅载入自己创建的文件）：

```bash
# 逐行导入，保留 JDBC URL 中的 & 和 Base64 末尾的 =，不执行.env内容。
while IFS= read -r entry || [ -n "$entry" ]; do
  entry=${entry%$'\r'}
  if [[ "$entry" =~ ^[A-Z_]+= ]]; then export "$entry"; fi
done < .env
./gradlew build
java -jar app/build/libs/communibuy.jar
# 或开发运行：./gradlew :app:bootRun
```

Windows PowerShell：

```powershell
Copy-Item .env.example .env
# 编辑 .env 后：
Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object {
    $parts = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
}
docker compose up -d --wait
.\gradlew.bat build
java -jar app/build/libs/communibuy.jar
```

可执行 JAR 唯一位置为 `app/build/libs/communibuy.jar`。移除了自定义修改 ZIP 时间与 manifest 的打包流程；不再需要 Python 才能构建，也不再复制产物到 `server/build/outputs`。Python 仅用于演示/检查脚本。应用缺少数据库或 JWT 配置时启动失败，禁止回退到 root、明文密码或模拟令牌。

停止数据库：`docker compose down`（保留卷）。不要对已有库执行 `down -v`。Compose 已按回环地址发布端口；生产应使用单独数据库和 TLS，不使用示例的关闭 TLS 参数。

## 首个管理员（可选、显式初始化）

普通演示通过公开注册创建 USER。若需管理员，设置 `BOOTSTRAP_ADMIN_ID` 与自己生成的 `BOOTSTRAP_ADMIN_PASSWORD`，启动一次：

```bash
java -jar app/build/libs/communibuy.jar --spring.profiles.active=bootstrap
```

启动时插入新管理员；重复 ID 会失败，不会覆盖或提升已有账号。初始化成功后停止进程、清除这两个环境变量，再按普通模式启动。绝不将该 profile 长期开启或把密码写入命令/仓库。正式环境由维护者在维护窗口执行。

## 测试

Linux `./gradlew clean build`；Windows `.\gradlew.bat clean build`。默认 Testcontainers 自动启动两个独立 `mysql:8.4.6` 容器，分别验业务及旧库迁移，要求可用的 Docker daemon。缺少 Docker 会失败，不会静默跳过。

没有 Docker 时，可以让测试连接**专用可丢弃** MySQL 实例，预先创建 `communibuy_test` 和 `communibuy_migration_test` 两个库，账号只授权这两个库。测试会删除表/测试数据，绝对不能传共享或生产库：

```bash
export TEST_DB_URL='jdbc:mysql://127.0.0.1:33317/communibuy_test?allowPublicKeyRetrieval=true&useSSL=false'
export TEST_MIGRATION_DB_URL='jdbc:mysql://127.0.0.1:33317/communibuy_migration_test?allowPublicKeyRetrieval=true&useSSL=false'
export TEST_DB_USER=your_scoped_test_user
export TEST_DB_PASSWORD=your_generated_test_password
export ALLOW_TEST_DB_RESET=yes
./gradlew clean build
python3 scripts/check-tests.py
```

PowerShell 使用 `$env:变量名='值'` 设置同名变量，再运行 `.\gradlew.bat clean build`、`python scripts/check-tests.py`。测试 URL 必须指向上述准确库名，并且显式启用 reset。不要在普通应用启动时设置测试变量，也不要把测试 JWT 密钥用到正式运行。

JUnit Platform 已显式启用。报告为 `app/build/reports/tests/test/index.html`，机器可读结果为 `app/build/test-results/test/TEST-*.xml`。CI 同时检查测试发现数量和零跳过，上传报告与可执行 JAR，并启动 JAR 执行 HTTP 演示。

## 可复现业务演示

在应用运行且连接本地隔离数据库时，另开终端：

```bash
python3 scripts/demo.py
# 改端口：python3 scripts/demo.py --base-url http://127.0.0.1:8081/api/v1
```

脚本每次生成新的卖家、买家和随机密码，不输出密码/令牌：注册 → 登录 → 卖家创建团购 → 买家下单 → 相同幂等键重试 → 模拟支付 → 卖家发货 → 买家收货。随后验证三个 403、取消前后库存及重复取消、注销后的 401。脚本会保留演示交易记录，无清库操作。

## 配置与限制

- `JWT_SECRET`：至少 32 个随机字节的 Base64；JWT HS256，校验签名/iss/aud/sub/iat/nbf/exp/jti/ver。默认 3600 秒，`JWT_TTL_SECONDS` 允许 60..86400。
- `PAYMENT_TIMEOUT_SECONDS`：1..604800，默认 900；`ORDER_SWEEP_DELAY_MS` 默认 60000。订单保存创建时的截止时间，改配置只影响新订单。每次处理至多 100 笔，积压下一轮继续，启动时也补偿。失败订单记录安全日志，需检查并处理。
- `LEGACY_PASSWORD_LOGIN` 默认 false；旧密码迁移只在维护者明确启用时允许，详见数据库文档。
- 实现为单体数据库事务；支持多个任务重复执行，不需要 Redis、消息队列或分布式锁。未接微信、短信、真实支付/退款、物流、上传；没有实现物理删除账号/商品/订单。
- 已实现登录/写入限流、受保护Actuator/Prometheus、精确CORS和审计，见[运行安全](../documents/server/operations.md)。HTTPS和反向代理部署见阶段6说明；优先同源 `/api`。

本次实际运行结果见 [验收记录](../documents/server/verification.md)。已有库必须先阅读[迁移说明](../documents/database/design.md)，不得用重建数据库替代升级。

## 企业商城增量（以progress.md验收状态为准）

新增分类、购物车勾选结算、按卖家拆单及明细、独立模拟支付、全额售后审核/退款重试、完成后评价与内部结算。使用`python3 scripts/commerce-demo.py`演示完整流程；旧demo.py仍可验证兼容接口。

支付默认禁用。仅本地课程演示显式设置`PAYMENT_MODE=simulator`、`PAYMENT_SIM_SECRET`（独立生成的至少32随机字节Base64）及`PAYMENT_MERCHANT`。`.env.example`只给空密钥，必须自己填写。`deploy` profile禁止模拟渠道，当前没有任何真实支付平台凭据或真实沙箱验收。

响应新增code与traceId，`X-Trace-Id`用于排障；管理员可获取`/v3/api-docs`，UI默认关闭。JaCoCo报告位于`app/build/reports/jacoco/test/html/index.html`，完整依赖锁为`app/gradle.lockfile`。新增/升级依赖需执行`./gradlew build --write-locks`并提交锁文件，不能只修改版本声明。

阶段、提交、自审、准确测试数量和限制见[进度记录](../documents/server/progress.md)，AI过程见[真实开发记录](../documents/server/ai-development-log.md)。原先验收记录是上一轮交付，不冒充本轮新增商城验收。
