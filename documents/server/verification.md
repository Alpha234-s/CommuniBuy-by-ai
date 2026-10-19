# 后端验收记录

验收日期：2026-10-19（Asia/Shanghai）。上游基线 `e2991cd`，独立本地任务分支 `backend/reliable-business-flow`。

## 实际执行

- 环境：Windows、Zulu JDK 21.0.8、Gradle Wrapper 8.14.3、MySQL Community 8.4.6 / InnoDB。
- MySQL 使用任务工作目录内的新数据目录，绑定127.0.0.1:33317；独立创建 communibuy_test、communibuy_migration_test 和 communibuy_demo，应用/测试使用仅授权这些库的随机凭据账号。未访问共享库、生产库或已有开发数据。
- 最终 `clean build --no-daemon`：**BUILD SUCCESSFUL**。`:app:test` 实际执行，`:app:bootJar` 生成 `app/build/libs/communibuy.jar`。Java编译从有效Gradle缓存恢复，测试重新运行。
- `python scripts/check-tests.py`：**34 tests / 0 failures / 0 errors / 0 skipped**。业务集成测试32项、迁移测试2项，所有关键一致性测试使用真实MySQL，没有H2。
- JAR 已用 `java -jar ... --server.port=18081` 实际启动，Flyway与Hibernate validate成功。标准库HTTP演示完成注册/登录、创建团购、下单与相同键重试、模拟支付、发货、收货、三个403、重复取消只回补一次、注销后的401。
- `git diff --check` 通过；Linux wrapper在Git中为100755。未修改webui，没有自动合并或强推。

## 测试覆盖

| 范围 | 实际覆盖 |
| --- | --- |
| 认证 | 缺失、旧模拟、伪造、错误签名、过期、未来签发、错误issuer/audience、缺失必要声明、注销、改密吊销全部会话、封禁 |
| 用户 | 私密资料归属、管理员能力、拒绝自行改role、响应无密码/哈希、重复注册及并发注册不覆盖、ID大小写、旧明文只升级一次 |
| 下单 | 拒绝伪造身份/价格/状态/时间；空值/零/负/浮点数量、无效ID/地址/价格；活动开始包含/结束排除、下架；价格/身份快照、64位金额 |
| 库存与事务 | 不同买家争最后一件；订单真实CHECK失败回滚；相同用户相同键并发与跨商品冲突；陈旧库存version拒绝；商品修改与下单竞争；容量保留给取消回补 |
| 流转 | 支付发货收货闭环、非法跳转、地址修改规则、隐私过滤；并发及重复取消、付款与取消竞争、卖家模拟退款与交易额排除、截止秒付款与超时竞争、重复扫描/重新构造任务后的补偿 |
| 查询与迁移 | 多条件AND、数据库分页/稳定排序/size限制、权限范围不被筛选绕过、真实统计和活动时间；旧SQL UNSIGNED与JPA旧宽字段、显式baseline、库存/历史账号/订单快照保留、FK/CHECK生效 |

## 构建过程发现并已修复

最初编译修正了关键字搜索转义和异常类导入。首轮27项测试有一项因MySQL CHECK异常包装类型的断言过窄失败，改为验证数据访问异常、实际CHECK名称及事务后库存/订单结果。扩展到34项后发现并发注册从EntityManager直接抛异常未映射409，改为Repository显式INSERT，随后完整通过。

最终clean build第一次因演示JAR仍在运行而受Windows文件锁阻止，停止本任务演示进程后重跑成功。沙箱内javac关闭依赖JAR受权限限制，实际Gradle验证在已授权的沙箱外执行，仍仅连接任务专用MySQL。没有把这些早期失败计为通过。

## 限制与未验证事项

- 本机没有Docker/gh CLI；Compose配置与Testcontainers自动启动容器路径未在本机执行。本机使用相同8.4.6引擎的独立MySQL实例验证关键语义。GitHub Actions已配置真实容器测试与JAR演示，远程执行情况以PR检查为准，不将未运行的CI描述为通过。
- Linux启动脚本来自Gradle8.14.3标准wrapper，已设置可执行位；本机未运行Linux环境，CI负责Linux检查。
- 旧库迁移仅针对仓库旧SQL与旧JPA宽字段演练，未对任何团队实际数据库执行。任意脏数据/私有结构变更需维护者先审计并在复制库演练；MySQL DDL失败不具备自动事务回滚。
- 前端未适配、真实支付/退款及微信/短信/物流未实现；课程演示是模拟资金流。HTTPS、同源代理、限流、监控及吊销表过期归档需按部署补充。没有高吞吐/长时间压力测试或跨数据库版本认证。

本机Git凭据存储不可用，命令行push失败；若通过已连接GitHub工具发布，将在PR说明和交付文件列出远程提交与本地提交的对应关系。提交树需一致，远程提交时间/SHA可不同。不修改个人fork的main或上游main。
