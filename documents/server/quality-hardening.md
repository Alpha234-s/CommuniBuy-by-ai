# 后端质量加固（2026-10-24）

基线：团队远端 `feat/backend` = `399df3de112f69d0bb51c823735a96004865f3e1`；main = e2991cd。工作区干净；任务分支 `fix/backend-quality-hardening` 直接从该后端基线创建。保留原企业商城交付证据，本轮不增加商城功能，不修改全局账务锁及已部署迁移。

| 要求 | 实现位置 | 验收标准/状态 |
| --- | --- | --- |
| 评价/直接下单/结算锁顺序 | OrderService、ShopService；ReviewLockIntegrationTest | 已完成：修复前2失败均1213/40001；修复后80项全量通过 |
| 测试分层及CI | Gradle、check-tests.py、backend.yml | 无库编译/单测，完整check含全部MySQL，Windows编译及Linux全量；待实施 |
| 安全异常诊断 | ApiErrors、定时任务 | trace、定位、原因类型可查，消息及请求秘密不泄漏；待实施 |
| 过期吊销清理 | BlacklistRepository、定时任务 | 索引分批、有界、Clock边界、未过期吊销和JWT过期均有效；待实施 |

环境：JDK21、Wrapper8.14.3、Boot3.5.16、原生隔离MySQL8.4.6。Windows本机无Docker，使用此前任务专用33317端口及精确命名可丢弃测试schema；不触碰其他本机MySQL。远端Git在受限shell因schannel凭据失败，授权shell正常fetch；连接器与fetch均核对到同一SHA。

## 阶段1验收与自审

`./gradlew :app:formatJava build --no-build-cache`（本机使用相同Wrapper的gradlew.bat）成功，80测试、0失败/错误/跳过；check-tests.py门槛从78随真实新增2项提高到80。原78项完整保留，包括防超卖、回滚、付款取消、超时、购物车最后库存、退款结算竞争、3项迁移。

修复前相同测试2失败：购买事务为死锁牺牲者，订单总数1而非2，库存2而非1；购物车结算批次0且购物车保留；评价仍1条、已完成订单保持3。MySQL1213/40001证明不是单纯等待超时。安全摘录[quality-review-lock.json](evidence/quality-review-lock.json)，完整XML仅任务私有目录保留。修复后两操作均返回、总订单2、评价1、库存1、明细数匹配；购物车路径批次1、已结算行移除。

测试拦截仅在test配置生效，先让购买获得用户锁，再放行评价：旧路径评价取得商品/订单后再放购买请求商品，从而构成FK循环；新路径评价先请求用户，购买可完成并释放用户。使用有界CountDownLatch/Future，线程结束后清理，不依赖sleep。生产无测试钩子、重试或锁超时修改。

自审：create/checkout/cartChange已有用户→商品（商品升序）；review是本次复现的反向用户外键路径，现保持相同顺序且复查封禁。支付记录无用户外键；账务相关仍先finance_mutex，不增加用户显式锁。不改数据库结构/历史迁移/API/前端；额外用户锁只在评价事务中串行同用户写操作。没有独立人工评审。

## 后续技术债（只评估）

支付/退款渠道调用仍在持锁事务中。接真实渠道前必须设计持久化操作意图、结果未知查询与对账，以及外部成功而本地回滚的补偿；模拟事务不能证明远端原子性。本轮不改变交易语义。

finance_mutex仍串行不同卖家的账务操作。后续先测支付/退款/结算混合负载、锁等待与P95，再评估按卖家等缩小锁范围；必须涵盖跨卖家操作和退款/结算不变量，本轮不删除、不替换。
