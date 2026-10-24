# 运行安全与可观测性

服务使用无Cookie的Authorization Bearer，Security无Session，因此关闭CSRF；如前端将令牌移入自动发送的Cookie，必须先改CSRF设计。CORS_ALLOWED_ORIGINS默认为空，只接受精确HTTP(S)源，用逗号分隔；不支持任意通配或携带Cookie。前端用textContent显示商品/用户/评价的纯文本，当前接口拒绝尖括号，不承诺富文本渲染。

默认每UTC分钟按连接IP限制登录10、注册10；登录后一般写入按用户300，订单/结算/退款/改密/角色等敏感操作60；渠道通知按IP300。达到上限返回429、RATE_LIMITED、Retry-After和traceId。固定分钟窗口、每实例内存最多10000个键，重启会重置，不是跨节点防攻击系统。local默认不解析X-Forwarded-For，代理后的登录限制按代理IP共享；deploy显式启用framework转发头解析，必须只允许可信代理访问应用且代理覆盖来访者的Forwarded/X-Forwarded-*，否则可伪造IP。生产入口可另加客户端限流，本应用保护仍保留。GET仍须通过认证权限，分页size≤100。

所有请求生成X-Trace-Id；只记录方法、路由模板、状态和耗时，不记录请求体、查询、完整URI、密码、token或地址。audit_events记录用户/商品/购物车/订单/支付/退款/评价/结算动作及结果，actor/resource为SHA256标识，非明文私密字段；哈希只是减少直接泄露，不是不可关联的匿名化。成功审计与业务同事务，持久化失败则业务回滚；业务异常审计采用独立事务，审计存储也失败时仅记录动作名告警，不掩盖原异常。提交阶段的数据库错误可能发生在切面返回后，应结合请求错误日志排查，不能声称所有提交失败都有审计。管理员GET /api/v1/audit?page=0&size=20可按trace关联，禁止普通用户访问。需要按学校数据保留策略单独安排审计归档，当前未自动清除。

Actuator的health/info/metrics/prometheus全部要求ADMIN Bearer，health不展示详情。可演示：

```bash
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://127.0.0.1:8080/actuator/health
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://127.0.0.1:8080/actuator/metrics/http.server.requests
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://127.0.0.1:8080/actuator/metrics/hikaricp.connections.active
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://127.0.0.1:8080/actuator/prometheus
```

Micrometer输出HTTP数量/耗时、JVM、Hikari连接和限流拒绝计数；业务trace在日志和审计，不作为高基数metrics标签。Prometheus使用独立管理员短期令牌bearer_token_file并在到期前轮换，凭据不提交仓库；本版未部署Grafana或自动刷新监控令牌。默认连接池2～10，获取连接超时10s，可用DB_POOL_MIN/MAX配置。V6增加订单/支付/退款/用户管理及审计索引；组合查询由数据库分页，JPA Criteria/JDBC占位参数，无用户字符串拼接SQL。

CI运行Java编译unchecked警告视为错误、固定版本google-java-format格式检查、全部真实MySQL集成测试、测试发现/零跳过检查、JaCoCo、JAR全流程和预设目标读取压测。未执行的远程CI不能标通过。覆盖率用于定位缺口，不以数字替代事务不变量验证；实际结果见最终验收报告。

## 安全异常排障

按响应头/响应体`traceId`检索`event=api.unexpected`（500）或`event=api.conflict`（409）。结构化诊断记录异常全类名、最多8层原因类型、每层抛出位置及至多4个堆栈位置（`类#方法:源码行号`）；SQL仅记录整数vendor和合法5字符SQLState。循环原因链会截断，不输出原始message、toString、suppressed内容、SQL文本/参数或Throwable对象；因此日志不含框架自动打印的原始原因消息。结合相同版本源码定位，必要时在隔离测试复现，不能为排障直接开启请求体/绑定值日志。

Hibernate原始SqlExceptionHelper、SQL及bind类别在默认配置关闭，避免其绕过安全渲染输出数据库中的私密值。部署者不要提高这些类别至DEBUG/TRACE或开启show-sql。这里是当前受控日志策略，不声称任意第三方自定义日志也会自动脱敏。客户端仍只收到原有安全错误码、消息和trace，无堆栈/API兼容变化。

订单超时扫描与逐单失败均使用同一安全诊断；每轮无HTTP上下文时生成trace，结束后清理或恢复原MDC。扫描失败留待下一轮；单个订单失败不阻断其余订单。审计存储失败也记录安全位置/原因类型，不覆盖原业务异常。SafeDiagnosticsTest捕获真实HTTP过滤器/异常处理日志及定时任务日志，覆盖消息、SQL原因链、密码、完整令牌、地址、正文/查询不泄漏与循环/深链边界。

## 吊销记录保留与清理

`blacklist.token_hash`保存随机jti，expire_at为UTC秒。清理边界是`expire_at <= Clock.now`，与JWT校验`exp > now`一致。启动补偿一批，此后固定delay（默认60秒），一次按expiry/PK稳定顺序删除最多500行。复用`ix_blacklist_expiry`，每批REQUIRES_NEW、READ_COMMITTED事务；多实例可重复处理，无进程内无限循环。积压继续下轮，删除失败整批回滚并留安全trace日志，下轮重试。配置范围见README，配置无效即使关闭调度也启动失败。

`event=auth.revocations.cleanup`只记录删除数量和trace；`auth.revocations.deferred`记录安全原因类型/位置，不记录jti或令牌。清理只去除已失去认证效力的记录，不代表清理用户会话或提前恢复权限。JWT过期检查、用户封禁和authVersion检查保持原样。无新迁移，已部署V1..V6一律不修改；不要手工清空blacklist来“修复”故障。
