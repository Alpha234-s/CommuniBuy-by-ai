# MySQL 结构、升级与密码迁移

## 当前约定

MySQL 8.4.6 / InnoDB / utf8mb4。用户ID用 utf8mb4_bin 区分大小写，不转小写、不截断；1..32个ASCII字母/数字/下划线，首位字母或下划线。资源ID为有符号BIGINT（Java Long），金额单价为非负INT分，总额为BIGINT分，时间为BIGINT UTC秒。代码与迁移共同维护，正式运行 `ddl-auto=validate`，没有自动清库或自动baseline。

| 表 | 字段与约束 |
| --- | --- |
| users | id主键；password仅哈希/显式标记的旧明文；password_scheme=BCRYPT或LEGACY_PLAIN；auth_version会话版本；role=-1/0/1；nickname64、description/address255 |
| items | id；owner_id外键users；name128、description TEXT；price、stock非负；status仅-1/0；start_time必填，end_time可空且大于开始；shipment_count；JPA version |
| orders | item_id/user_id/owner_id外键；quantity>0；price与total成交快照；address必填；status=-1/0/1/2/3；create_time必填，其他业务时间可空；expires_at；idempotency_key/request_hash；refund_status |
| blacklist | token_hash主键存JWT随机jti（保留旧字段名以减少迁移）；expire_at，用于过期归档 |

订单 `(user_id,idempotency_key)` 唯一；用户/卖家/商品+状态+ID索引支持查询，状态+expires_at+ID支持超时扫描。商品owner/status/ID及状态/活动时间有索引。外键默认限制删除，不级联删除交易记录；没有公开物理删除接口。CHECK校验价格/库存/数量/状态/活动时间和total快照公式。

Flyway目录 `server/app/src/main/resources/db/migration`：

- V1创建旧模型的列集，供空库从零迁移；新库随后执行V2。
- V2增加安全/幂等/截止字段，规范角色、状态、大小写与类型，补FK/CHECK/唯一键/索引。历史商品 `status>=0` 移到shipment_count，status置0；负数置-1。role负数置-1、正数置1、其余置0。end_time<0置NULL（无限），start_time空或负置0。旧订单total按成交price*quantity计算，expires_at=create_time+900，幂等键为legacy-ID。历史记录和库存不清除。
- 已部署迁移禁止再编辑；后续改结构新增V3等版本。新账户由服务显式写BCRYPT；旧记录默认LEGACY_PLAIN，密码内容不会在SQL迁移中改写。

## 新的本地数据库

使用 `server/compose.yml`，仅发布回环3307端口。Compose用独立随机root密码创建库与communibuy用户；应用使用communibuy，权限仅限该库。正式部署建议单独迁移账号（DDL）与运行账号（SELECT/INSERT/UPDATE），先用迁移账号执行Flyway，再用运行账号启动并设置 `SPRING_FLYWAY_ENABLED=false`。后端无需DELETE核心业务表权限；禁止使用root作为常规连接。

DB_URL、DB_USER、DB_PASSWORD、JWT_SECRET必须通过环境提供。`.env.example`无真实秘密。示例关闭TLS和允许公钥获取仅供本地演示，正式连接按部署配置TLS。不要将真实库的导出、密码或令牌提交仓库。

## 已有数据库：必须逐步升级，不能重建代替

**本次仅在独立测试库演练，没有操作团队共享库或生产库。** 下面步骤由维护者在确认库名、备份和维护窗口后执行。

1. 停止旧应用写入，备份结构和数据（mysqldump --single-transaction --routines --triggers，凭据通过安全配置提供）。记录MySQL版本、数据库名称、表定义、行数、现有库存和未完成订单。先把备份恢复到新的演练库；原库保持不变。
2. 比较实际 `SHOW CREATE TABLE` 与e2991cd原型列集。旧Hibernate可能将String建成VARCHAR(255)、SQL脚本使用UNSIGNED，两种情况V2均有MySQL测试。若已有自定义索引/外键/其他结构，不可直接baseline：先检查同名约束和类型转换，制定独立迁移。不要自动猜测未知版本。
3. 在复制库运行下面审计，所有异常必须由业务负责人核实后定向修复，不能删掉记录让迁移通过：

```sql
SELECT id FROM users WHERE CHAR_LENGTH(id)>32
 OR NOT REGEXP_LIKE(id,'^[A-Za-z_][A-Za-z0-9_]{0,31}$','c')
 OR password IS NULL;
SELECT id FROM items WHERE owner_id IS NULL OR CHAR_LENGTH(owner_id)>32
 OR name IS NULL OR CHAR_LENGTH(name)>128
 OR price<0 OR price>2147483647 OR stock<0 OR stock>2147483647
 OR (end_time>=0 AND end_time<=GREATEST(COALESCE(start_time,0),0));
SELECT i.id FROM items i LEFT JOIN users u ON BINARY u.id=BINARY i.owner_id WHERE u.id IS NULL;
SELECT o.id FROM orders o
 LEFT JOIN items i ON i.id=o.item_id
 LEFT JOIN users b ON BINARY b.id=BINARY o.user_id
 LEFT JOIN users s ON BINARY s.id=BINARY o.owner_id
 WHERE i.id IS NULL OR b.id IS NULL OR s.id IS NULL
 OR CHAR_LENGTH(o.user_id)>32 OR CHAR_LENGTH(o.owner_id)>32
 OR o.quantity<=0 OR o.quantity>2147483647 OR o.price<0 OR o.price>2147483647
 OR o.address IS NULL OR TRIM(o.address)='' OR o.create_time IS NULL
 OR o.status NOT IN(-1,0,1,2,3);
SELECT token_hash,COUNT(*) FROM blacklist GROUP BY token_hash HAVING COUNT(*)>1;
SELECT i.id FROM items i JOIN orders o ON o.item_id=i.id AND o.status IN(0,1)
 GROUP BY i.id,i.stock HAVING i.stock+SUM(o.quantity)>2147483647;
```

还需检查NULL角色、异常业务时间、资源ID超过有符号Long范围、非法数量造成的历史库存偏差、历史订单买卖方与商品归属关系。旧原型写死的 `user_id_from_token`/`owner_id_from_item` 可能不是有效账号；必须从业务记录恢复真实归属，无法确认就记录并由维护者决策，不能自动造账号掩盖问题。已有重复吊销记录应审计后去重，迁移不会自动删除。用户ID转换为大小写敏感后，按精确大小写核对外键，不能靠旧库忽略大小写的匹配通过审计。

4. 若原库没有Flyway历史表，**只对已确认的旧原型结构**执行显式baselineVersion=1。使用与应用兼容的Flyway CLI（11.x），URL/用户/密码通过 `FLYWAY_URL`、`FLYWAY_USER`、`FLYWAY_PASSWORD` 配置。命令在server目录：

```bash
flyway -baselineVersion=1 baseline
flyway -locations=filesystem:app/src/main/resources/db/migration migrate
flyway -locations=filesystem:app/src/main/resources/db/migration validate
```

已有Flyway历史则不再baseline，先validate和比较校验和。空库无需baseline，应用自动从V1迁移。应用默认拒绝无历史的非空库，禁止长期启用baseline-on-migrate。不要通过设置ddl-auto=update绕过迁移。

5. 对比迁移前后行数、精确ID、价格/数量/total、库存、发货计数和订单状态。尤其旧待支付订单按旧create_time+900计算截止，启动新服务会关闭已超时订单并回补其已预留库存；**先确认旧库存确实扣减过**，否则应在维护窗口对账修复后再启用扫描。通过 `--app.orders.scheduling-enabled=false` 可在演练期禁用自动扫描，校验完成后移除此参数；截止时间仍阻止过期支付。
6. 先在复制库执行完整演示、MySQL测试及旧账号登录迁移。确认后才在维护窗口对原库执行相同步骤，再用新应用的validate检查结构。保留备份和审计结果；本任务不会自动执行这一步。

**失败与回退：** MySQL DDL隐式提交，Flyway失败不代表已回滚结构。停止写入，记录失败版本和已完成DDL，从已验证备份恢复到单独数据库并核对，或由维护者针对部分执行状态制定修复迁移。不要盲目repair、删历史表后重跑、自动drop/recreate或恢复运行旧应用到新结构。存在新业务写入时，必须先保留并对账新增交易，不能直接恢复旧备份覆盖。

## 明文密码迁移

V2只增加 `password_scheme='LEGACY_PLAIN'` 标记，保留原值。普通运行 `LEGACY_PASSWORD_LOGIN=false`，旧明文不能登录；新注册一律BCrypt。维护者明确开启 `LEGACY_PASSWORD_LOGIN=true` 后，只有标记为LEGACY_PLAIN的用户可使用旧密码：成功校验后，在用户行锁事务内写入BCrypt、标记BCRYPT、增加auth_version，随后签发新令牌。重复登录只校验BCrypt，绝不重复哈希。登录失败不改数据，封禁仍拒绝。所有旧模拟token无条件失效，不具备迁移登录能力。

先审计密码来源，若自定义版本已存BCrypt，维护者必须验证算法后将相应行scheme显式设BCRYPT；不能对其当明文再哈希，也不根据 `$2` 前缀自动猜测（旧明文可能碰巧同形）。禁止导出或记录密码内容。完成升级后关闭兼容开关；查询 `SELECT password_scheme,COUNT(*) FROM users GROUP BY password_scheme` 跟踪剩余数量，不显示密码。尚未迁移的账号可保留到下次维护窗口开启兼容，或经用户确认做安全重置；无邮件/短信找回能力。超过72 UTF-8字节的旧密码需人工安排重置，不可截断或静默改变。

维护者应在评审中确认兼容窗口和剩余旧账号处理；不应在正式环境长期启用明文兼容。开发测试固定密码及测试密钥仅在src/test，正式启动没有默认管理员密码。bootstrap管理员只能显式设置环境并插入不存在的ID，不能覆盖旧账号。

## 验证范围

真实MySQL测试覆盖旧SQL UNSIGNED结构、旧JPA VARCHAR(255)、显式baseline、V2保留数据/计数/密码标记/订单快照及FK/CHECK。脏数据、任意同学私有结构变更、云MySQL权限/排序规则差异、共享库的真实升级均不在自动验证范围，必须先复制库演练。
