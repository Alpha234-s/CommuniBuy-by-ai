# 后端可靠性任务记录

- 负责人：当前独立任务分支维护者；目标为课程可演示的认证/交易闭环，最终由其他后端维护者评审。
- 基线：团队upstream/main `e2991cd`，开始和提交前均重新fetch核对；仓库AGENTS.md为空。新检出无他人未提交修改，使用 `backend/reliable-business-flow`，个人fork为origin。
- 范围：server、后端/数据库文档、README、后端CI；未修改webui。
- 验收：真实签名认证、角色/资源归属、DTO无秘密、MySQL并发库存/事务/幂等、订单状态/超时/回补、Flyway旧库迁移、分页筛选统计、Boot JAR启动和HTTP演示。
- 审查线索在最新基线上逐项确认仍存在：模拟JWT；控制器不验token；角色及实体泄露；明文密码/伪注销改密；固定订单身份/客户端价格；无锁及数量时间校验；任意状态时间更新；空请求删除约定；if-return忽略后续筛选；伪分页/统计占位；status混合发货次数；ddl-auto=update/root配置；缺失Linux wrapper/JUnit Platform和CI；协作说明仍写Hello World。
- 实施阶段：核心认证与事务规则/迁移和构建；真实MySQL验收测试；启动演示、接口/旧库迁移文档及CI。没有共享数据库操作，没有已合并的其他修复被覆盖。
- 兼容性与风险：旧模拟token全部失效；新Bearer/统一data与分页/动作接口/商品version需前端适配。MySQL DDL不可事务回滚，旧库必须备份审计、复制库演练后显式baseline；旧明文默认禁用登录，按数据库文档升级。
- 实际验证、提交和限制见verification及Draft PR。没有自动合并授权。
