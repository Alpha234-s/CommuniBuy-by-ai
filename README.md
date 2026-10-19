# CommuniBuy

社区团购课程项目：Spring Boot / Spring Data JPA / MySQL / Gradle 后端与独立前端。

- [后端启动、构建、测试与演示](server/README.md)
- [接口契约与前端适配](documents/server/design-api.md)
- [事务、权限与状态设计](documents/server/design.md)
- [数据库与旧密码迁移](documents/database/design.md)
- [协作约定](server/CONTRIBUTING.md)

已实现账号注册登录、权限与归属控制、库存预留、幂等下单、模拟支付/退款、发货收货、取消及超时释放。支付是课程演示模拟，不接真实支付平台。前端尚需按新接口契约联调，本次未修改 `webui/`。
