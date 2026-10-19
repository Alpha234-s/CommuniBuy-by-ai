# 可复现部署、备份与回退

本轮本地使用JDK21.0.8、Gradle8.14.3、Spring Boot3.5.16、MySQL8.4.6；依赖以server/app/gradle.lockfile为准。Docker镜像选择固定版本Temurin21.0.8_9-jre-jammy、MySQL8.4.6、Nginx1.28.0-alpine；升级安全补丁需重新验收。正式交付镜像应记录实际pull后的RepoDigest并固定，不宣称浮动tag可永久字节复现。Temurin参考[官方镜像](https://hub.docker.com/_/eclipse-temurin)。本机没有Docker CLI/daemon，容器与Nginx/TLS实际运行结果必须看最终验收，不能用JAR结果代替。

## 构建与本地JAR

从仓库根目录进入server。需要Java21、Python3和Docker Compose v2；测试默认启动两套Testcontainers真实MySQL，缺Docker会失败而不是跳过。

```bash
./gradlew clean build --no-daemon
python3 scripts/check-tests.py
cp .env.example .env
# 在编辑器中填写独立随机DB_PASSWORD、MYSQL_ROOT_PASSWORD、JWT_SECRET、PAYMENT_SIM_SECRET。
# openssl rand -hex 24（数据库）；openssl rand -base64 48（两个独立密钥）。禁止复用示例或打印进日志。
# 逐行按字面加载，不能source带&的JDBC URL；仅加载本人的可信.env。
while IFS= read -r line || [ -n "$line" ]; do
  line=${line%$'\r'}
  case "$line" in ''|'#'*) continue;; esac
  export "$line"
done < .env
docker compose up -d --wait mysql
java -jar app/build/libs/communibuy.jar
```

PowerShell对应命令：

```powershell
.\gradlew.bat clean build --no-daemon
python scripts/check-tests.py
Get-Content .env | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object {
  $pair = $_.Split('=', 2)
  [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
}
docker compose up -d --wait mysql
java -jar app/build/libs/communibuy.jar
```

无Docker时需独立MySQL8.4.6，预建communibuy_test、communibuy_migration_test并授专用用户这两个库的权限。仅测试进程设置TEST_DB_URL/TEST_MIGRATION_DB_URL/TEST_DB_USER/TEST_DB_PASSWORD及ALLOW_TEST_DB_RESET=yes；URL库名必须精确匹配。测试会清理这两个库，绝不可指向业务库。完整示例见server/README.md。应用正常使用非root DB_USER，只授权业务库；本地允许DDL供Flyway迁移，生产可分离迁移用户与仅DML运行用户，但需维护者执行迁移并核对validate，不会自动提权。

## 完整容器与代理

```bash
# 已构建并验收上面的JAR、填写.env后
docker compose --profile demo up -d --build --wait
python3 scripts/commerce-demo.py --wait-seconds 90
docker compose --profile proxy up -d --build --wait
docker compose exec -T proxy nginx -t
docker compose exec -T app id
python3 scripts/commerce-demo.py --base-url http://127.0.0.1:8088/api/v1 --wait-seconds 90
docker compose logs --tail=80 app
docker compose down
```

down保留数据库卷，**不要加-v**。Dockerfile运行UID10001、只读根目录、/tmp内存挂载，镜像只复制验收JAR，不带源代码、.env或密码。容器healthcheck只测TCP监听，不等同业务健康；脚本另执行登录与全业务验收，管理员Actuator检查数据库。Compose默认只启动MySQL；demo/proxy才启动应用，避免与本机JAR同时占用8080。数据库3307、应用8080、代理8088均只绑定loopback。

正式部署不公开数据库/应用端口，只由可信代理进入。设置APP_PROFILES=deploy，模拟与明文兼容被强制禁用；本版真实支付尚未接入，因此deploy环境不能完成资金流程。课程可在明确隔离的local演示环境启用SIMULATOR。按deploy/nginx-https.conf.example替换学校域名并挂载正式签发的证书、映射443，校验证书链/续期/过期告警；当前没有域名/证书，不声称公网HTTPS已验收。代理覆盖Forwarded和X-Forwarded-*，不直接暴露信任代理头的deploy应用。不要记录带查询的原始访问日志；应用记录脱敏路由。证书私钥不入镜像或仓库。

## 初始化与演示

不提供静态管理员密码或自动seed。全流程脚本每次准备3个独立普通账号、随机密码，不输出秘密，保留交易：

```bash
python3 scripts/commerce-demo.py --wait-seconds 90
python3 scripts/load-test.py --output app/build/performance.json --jar app/build/libs/communibuy.jar
```

演示依次注册/登录→卖家发起3件团购→买家购物车勾选→跨两卖家原子下单→原幂等键重试→独立模拟支付→卖家发货→买家收货→逐明细评价→两卖家内部结算→买家全额售后→卖家批准模拟退款→下一结算批负数扣减。另验证越权403、重复取消只回补一次、注销后401。真实支付平台沙箱、银行打款、退货物流均不在脚本中。

首个管理员由维护者临时设置BOOTSTRAP_ADMIN_ID和BOOTSTRAP_ADMIN_PASSWORD，**仅一次**用`java -jar app/build/libs/communibuy.jar --spring.profiles.active=local,bootstrap`（正式用deploy,bootstrap且支付禁用）。创建成功后停止、从环境移除这两个变量，按普通profile重启。重复ID拒绝并停止启动，不覆盖账号；bootstrap不应长期保留。之后管理员POST /user创建普通账号并通过单独role操作授权，严禁前端资料更新携带role。

## 数据库升级

空库由应用Flyway自动V1→V6，Hibernate只validate。已有Flyway库先validate，再升级；无历史的原型库须完成[旧库审计](../database/design.md)并显式baselineVersion=1。绝不自动清库/repair/删除历史表。旧固定身份/孤立引用/已扣库存不明的订单必须人工对账；不能造用户或抹掉交易让迁移通过。明文旧密码默认不能登录，显式临时兼容后按scheme一次性升级，详见同文档。

## 备份与恢复演练

需要MySQL8.4客户端，凭据只在进程环境：DB_HOST、DB_PORT、DB_USER、DB_PASSWORD、DB_NAME。专用用户应有被备份库SELECT/SHOW VIEW/TRIGGER等必要权限。脚本以MYSQL_PWD传给客户端，命令行不带密码；环境仍可被同权限进程读取，应使用隔离运维会话。输出SQL含密码哈希/私密业务字段，只存受限备份盘，传输与存储加密，不放仓库/交付包。

```bash
python3 scripts/database-copy.py backup --file /secure/communibuy-20261019.sql
sha256sum /secure/communibuy-20261019.sql
# 由DB管理员先创建独立的空communibuy_restore_rehearsal并授上述用户权限，不能恢复到源库。
python3 scripts/database-copy.py restore --file /secure/communibuy-20261019.sql --restore-target communibuy_restore_rehearsal
# Windows可加 --mysql-bin-directory C:/mysql/bin
```

备份使用single-transaction/no-tablespaces/set-gtid-purged=OFF/hex-blob，当前全InnoDB，无存储例程；备份期间禁止DDL。restore只接受单独、已存在、零表的communibuy_restore_*，绝不drop或覆盖。失败保留部分文件/目标供检查，不盲目重跑。恢复后比较所有表行数、订单明细合计、支付/退款/结算净额，启动相同V6应用校验Flyway并跑权限/业务回归；备份完成不等于恢复成功。本轮实际演练证据见evidence/deployment.json，不含SQL备份或秘密。

## 版本回退

```bash
# 先停写/停止当前服务并备份；只选择经演练且理解V6账务/明细语义的上一JAR。
java -jar /releases/previous-v6-compatible.jar --spring.profiles.active=deploy
# 容器发布先保存实际镜像digest；回退用该digest运行，不能把local标签当不可变版本。
```

不执行Flyway undo/drop；V4多明细后禁止回到只认单商品的旧写版本，V5后禁止无支付账本的旧版本。回退二进制也不应产生新数据损失；如必须恢复旧备份，先保留并对账备份后所有交易，由维护者决定停机切库，不能自动覆盖线上库。本轮只证明阶段5理解V6的JAR可在恢复库启动读取并保持数据；不证明任意历史版本可回退。MySQL DDL隐式提交，失败需针对已执行DDL人工处理。
