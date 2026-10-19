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
