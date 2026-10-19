# 阶段1验收与自审

日期2026-10-19，基础为上一轮7052ee7，最新团队main经GitHub连接器读取确认为e2991cd。

已实现并验收：统一Api新增code/traceId，HTTP语义保持；服务端trace与MDC生命周期；路由模板访问日志及脱敏测试；受ADMIN保护的OpenAPI；local/deploy配置；Hikari明确上限；springdoc2.8.17、JaCoCo0.8.15及依赖锁定。复用已存在的标准wrapper、Boot JAR和Flyway V1/V2，不重写已修复代码、不编辑已部署迁移。

实际环境为Windows、Zulu JDK21.0.8、Gradle8.14.3、MySQL8.4.6/InnoDB，任务专用33317实例。最终 `server/gradlew.bat build --write-locks --no-daemon` 成功，JUnit XML为35项业务测试+2项迁移测试，共37项，0失败/错误/跳过。日志脱敏、错误码/traceId、OpenAPI权限为本轮新增回归。MigrationTest分别验证旧SQL UNSIGNED和旧JPA宽字段的显式baseline/升级；业务库从既有版本validate通过。空库V1/V2安装也已由上一轮测试，但本轮新隔离库重建验证将在后续部署阶段复验，不混淆两轮结果。

当前JAR已启动于18083，HTTP脚本实测：注册登录→团购→下单重试→支付→发货→收货→403→取消回补→注销401。不是只运行编译或Mock测试。

JaCoCo XML统计：指令2520覆盖/314未覆盖，行281/28，分支203/80，方法109/20。未覆盖主要包括启动bootstrap管理员、极端long溢出与不可用算法等防御分支、部分权限/异常分支；不为数字编造不可达测试，后续安全与业务阶段继续补关键行为。

首次默认环境失败：Git所有权、DNS/旧代理不可用、Gradle daemon连接受限；外部执行恢复。首次新增测试运行因MySQL启动于不同受限环境导致3个初始化失败，改在同一外部环境启动任务专用MySQL后37项通过，没有删测试或改变业务预期掩盖环境问题。

自审发现与处理：客户端trace头存在日志注入风险，采用服务端生成且测试不反射；原异常响应缺稳定错误码，补充状态对应code；查询参数可能含秘密，日志仅使用路由模板；默认开放文档会泄露结构，限定ADMIN且禁用UI。确认实体不作为请求、秘密不写入日志、V1/V2未改、webui未改。

补充最终迁移验收：新增emptyDatabaseMigratesEveryVersion，本轮清空的是显式授权的communibuy_migration_test专用库，实际从空库执行V1/V2并validate成功。最终38项测试（35业务+3迁移）零失败/错误/跳过。原先37项结果为中间结果，不替代本项门槛。

剩余边界：Docker/实际Linux未验证；不存在外部支付凭据；最新main的shell fetch受网络限制，但连接器已读到提交SHA。生产部署与备份恢复是阶段6门槛，不在本阶段提前宣称完成。
