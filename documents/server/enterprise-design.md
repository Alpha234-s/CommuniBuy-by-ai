# 企业商城增量设计与兼容契约

本轮状态以progress.md为准，以下区分已实现与后续阶段计划。

## 阶段1（验收前不记完成）

继续使用JDK21、Wrapper8.14.3、Boot3.5.16、MySQL8.4.6；增加springdoc2.8.17（官方Boot3.5适配2.8.x）和JaCoCo0.8.15。实际解析的完整版本由Gradle依赖报告/锁文件记录，不能据声明版本推断运行成功。

Api保持success/message/data，新增code与traceId；成功code=OK，错误使用INVALID_REQUEST、UNAUTHENTICATED、FORBIDDEN、NOT_FOUND、CONFLICT、METHOD_NOT_ALLOWED、INTERNAL_ERROR等稳定枚举字符串。HTTP状态语义不变，前端可忽略新增字段，排障展示traceId。

每次请求由服务端生成32位随机X-Trace-Id，响应体与响应头一致；不反射不可信trace头。请求日志仅记录method、路由模板、status和durationMs，禁止query、body、身份凭据、完整token与地址。通过MDC串联应用日志，finally移除，防止线程复用串号。

OpenAPI `/v3/api-docs` 仅ADMIN，默认禁用Swagger UI；不对外暴露模型元数据。springdoc只描述控制器DTO，不直接公开实体。API与Bearer security scheme自动生成；需要浏览器可交互文档时由开发环境明确配置，不能降低正式权限。

默认local和显式deploy配置均无数据库/JWT默认秘密，均使用validate和Flyway。deploy禁用明文兼容及SwaggerUI；具体可信代理/HTTPS边界在阶段6配置。旧库不修改V1/V2，依照database/design.md的备份、孤立引用和固定身份审计，再显式baseline升级。

## 后续业务演进（计划，未宣称实现）

- USER是平台账号，买/卖由订单归属决定；同一账号可参与其他团购，也可发起自己的团购。保留禁止自购防止虚增交易。
- 保留单商品下单接口作为兼容入口；新增购物车结算批次与订单明细，按卖家分单，同一数据库事务原子提交。旧order主字段用于兼容摘要，明细是新交易的事实来源。
- 支付记录与渠道分离，缺凭据只实现隔离模拟及签名通知测试，真实平台沙箱验收保持未完成。
- 退款申请、审核、通道执行与通知分别留记录；退款总额不能超过实付，内部结算以已完成且未全额退款订单为范围，退款后通过账务调整核对。阶段4确定细节并以迁移/测试锁定。

参考：[springdoc兼容矩阵](https://springdoc.org/v2/)、[JaCoCo变更记录](https://www.jacoco.org/jacoco/trunk/doc/changes.html)。
