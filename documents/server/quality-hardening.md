# 后端质量加固（2026-10-24）

基线：团队远端 `feat/backend` = `399df3de112f69d0bb51c823735a96004865f3e1`；main = e2991cd。工作区干净；任务分支 `fix/backend-quality-hardening` 直接从该后端基线创建。保留原企业商城交付证据，本轮不增加商城功能，不修改全局账务锁及已部署迁移。

| 要求 | 实现位置 | 验收标准/状态 |
| --- | --- | --- |
| 评价/直接下单/结算锁顺序 | OrderService、ShopService；ReviewLockIntegrationTest | 已完成：修复前2失败均1213/40001；修复后80项全量通过 |
| 测试分层及CI | Gradle、check-tests.py、backend.yml | 本地通过：单测6+MySQL80；Windows无库编译/单测可用；新增Windows CI，远程待运行 |
| 安全异常诊断 | ApiErrors、SafeDiagnostics、定时任务 | 已完成：10单测+80MySQL全部通过；4日志测试覆盖trace/定位/脱敏/任务恢复 |
| 过期吊销清理 | BlacklistRepository、定时任务 | 索引分批、有界、Clock边界、未过期吊销和JWT过期均有效；待实施 |

环境：JDK21、Wrapper8.14.3、Boot3.5.16、原生隔离MySQL8.4.6。Windows本机无Docker，使用此前任务专用33317端口及精确命名可丢弃测试schema；不触碰其他本机MySQL。远端Git在受限shell因schannel凭据失败，授权shell正常fetch；连接器与fetch均核对到同一SHA。

## 阶段1验收与自审

`./gradlew :app:formatJava build --no-build-cache`（本机使用相同Wrapper的gradlew.bat）成功，80测试、0失败/错误/跳过；check-tests.py门槛从78随真实新增2项提高到80。原78项完整保留，包括防超卖、回滚、付款取消、超时、购物车最后库存、退款结算竞争、3项迁移。

修复前相同测试2失败：购买事务为死锁牺牲者，订单总数1而非2，库存2而非1；购物车结算批次0且购物车保留；评价仍1条、已完成订单保持3。MySQL1213/40001证明不是单纯等待超时。安全摘录[quality-review-lock.json](evidence/quality-review-lock.json)，完整XML仅任务私有目录保留。修复后两操作均返回、总订单2、评价1、库存1、明细数匹配；购物车路径批次1、已结算行移除。

测试拦截仅在test配置生效，先让购买获得用户锁，再放行评价：旧路径评价取得商品/订单后再放购买请求商品，从而构成FK循环；新路径评价先请求用户，购买可完成并释放用户。使用有界CountDownLatch/Future，线程结束后清理，不依赖sleep。生产无测试钩子、重试或锁超时修改。

自审：create/checkout/cartChange已有用户→商品（商品升序）；review是本次复现的反向用户外键路径，现保持相同顺序且复查封禁。支付记录无用户外键；账务相关仍先finance_mutex，不增加用户显式锁。不改数据库结构/历史迁移/API/前端；额外用户锁只在评价事务中串行同用户写操作。没有独立人工评审。

## 阶段2验收与自审

阶段1提交`9112e87`。保留同一source set，通过mysql标签分层，父fixture标签继承，AuthPolicy/Migration独立标注。`test`仅单元；`integrationTest`真实数据库；`check/build/jacocoTestReport`均显式依赖两层。Test禁止缓存/UP-TO-DATE，根套件0发现或存在跳过直接失败。CI检查两份XML与关键suite、数量下限和跨层重复，报告上传原目录通配已覆盖新路径。

无任何TEST_DB配置实际运行Windows `gradlew.bat :app:formatJava :app:classes :app:testClasses :app:test :app:devJar --no-build-cache`成功：6项单测，0失败/错误/跳过；不触发integrationTest或报告依赖。新单测验证金额/数量溢出、BCrypt UTF8字节边界与专用库双URL/重置授权/凭据/库名保护。原生数据库验收另行执行，不把编译或调试JAR当验收。

故意清除数据库配置、无Docker运行`:app:integrationTest --tests '*MigrationTest'`，按预期3项初始化失败、0跳过；根原因明确Docker unavailable及完整TEST_DB配置方法。该负向探测不是通过的业务测试。随后正确专用库配置`gradlew.bat clean build --no-build-cache`成功：单测6+MySQL80=86、0失败/错误/跳过，脚本复核通过；两个JaCoCo exec实际生成并合并，证据[quality-phase2.json](evidence/quality-phase2.json)。

自审：取消test的报告finalizer，防止快速单测被报告依赖带入MySQL；完整check显式包含报告及集成，防止移出默认任务漏测。环境缺失与连接失败不输出URL/凭据；原reset精确schema保护保留并补双URL和非空凭据校验。CI新增Windows+JDK21只编译，Linux保留完整MySQL/JAR/容器/代理；本轮远程尚未运行，不能拿旧78项CI替代。依赖/SQL/API未变化；开发者原`:app:test`命令现仅单测，前端无需调整。

## 后续技术债（只评估）

## 阶段3验收与自审

阶段2提交`370a2fe`。原ApiErrors确实仅输出类名；订单超时逐单也相同，扫描异常还会交由调度框架原样记录。本轮SafeDiagnostics只渲染类型、受限堆栈位置、合法SQLState/整数vendor及有界原因链；不把Throwable交给logger，不取message/toString/suppressed。HTTP500/409、超时扫描/逐单及审计存储降级复用。关闭Hibernate原始SQL异常/bind日志以防旁路泄漏。客户端契约不变。

日志捕获4项均通过：HTTP请求同trace关联响应/访问/异常，包含类#方法:行号和SQLException类型；密码/完整三段令牌/地址/正文/查询/模拟SQL参数及原始SQL不泄漏；深链/循环和非法SQLState有界处理；定时任务失败不阻断其他订单，扫描故障下一轮恢复且MDC正确恢复。测试为独立MockMvc过滤器/Advice加真实Logback捕获，无数据库依赖；不把它描述为真实SQL故障测试。

`gradlew.bat :app:formatJava build --no-build-cache`成功，10单测+80真实MySQL=90，0失败/错误/跳过；包括更改后完整令牌/SQL消息用例和所有受影响事务测试。合并JaCoCo见[quality-phase3.json](evidence/quality-phase3.json)。自审确认没有原异常消息/请求记录、没有改变安全响应或吞掉API异常；后台仅延期处理并留定位日志，不更改订单状态机。异常框架类型与源码行足够进行隔离复现，不声称可从日志恢复被刻意排除的原始敏感参数。外部人工评审及本轮远程CI仍待完成。

支付/退款渠道调用仍在持锁事务中。接真实渠道前必须设计持久化操作意图、结果未知查询与对账，以及外部成功而本地回滚的补偿；模拟事务不能证明远端原子性。本轮不改变交易语义。

finance_mutex仍串行不同卖家的账务操作。后续先测支付/退款/结算混合负载、锁等待与P95，再评估按卖家等缩小锁范围；必须涵盖跨卖家操作和退款/结算不变量，本轮不删除、不替换。
