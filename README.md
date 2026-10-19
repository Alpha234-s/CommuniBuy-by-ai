# CommuniBuy

社区团购课程项目：Spring Boot / Spring Data JPA / MySQL / Gradle 后端与独立前端。

- [后端启动、构建、测试与演示](server/README.md)
- [接口契约与前端适配](documents/server/design-api.md)
- [事务、权限与状态设计](documents/server/design.md)
- [数据库与旧密码迁移](documents/database/design.md)
- [协作约定](server/CONTRIBUTING.md)

已实现认证授权、分类搜索、购物车原子拆单、库存预留、幂等下单、独立模拟支付/退款、发货收货、售后评价和内部结算，以及traceId、审计、限流、受保护指标、MySQL迁移与集成测试。支付为隔离SIMULATOR，没有真实平台沙箱或银行打款。前端需按新契约联调，本次未修改 `webui/`。

- [阶段状态与真实验证证据](documents/server/progress.md)
- [部署、演示、备份、恢复与回退命令](documents/server/deployment.md)
- [权限矩阵、ER图和状态](documents/server/model-and-permissions.md)
- [最终验收矩阵](documents/server/final-acceptance.md)、[AI开发记录](documents/server/ai-development-log.md)
