> 2026-10-24新增质量加固轮次以团队feat/backend399df3d为基线，当前状态、分层命令、测试和CI结果见[本轮验收](quality-hardening.md)。下文78项及旧Actions为上一轮企业商城交付证据，不代表本轮代码已验证。
>
> 上一轮最终代码验收：本地1f6b5b3 / 远端6748ace76082eeb4fe04ecfc474df312b56da3ef；[Actions成功](https://github.com/Alpha234-s/CommuniBuy-by-ai/actions/runs/39261266487)，78项0失败/错误/跳过，JAR与容器代理全闭环通过。真实支付平台与公网TLS未验收。

# 企业商城验收矩阵（按实际证据更新）

本轮从7052ee7已有修复增量实施，没有覆盖已有认证/库存测试；上游main核对为e2991cd。分支feat/backend-enterprise。上一轮verification.md不是本轮结果。本报告与progress.md一起维护，未验证项不能算通过。

| 课程要求 | 代码/设计证据 | 验收证据/状态 |
| --- | --- | --- |
| 构建、跨平台Wrapper、标准可执行JAR | [构建](../../server/app/build.gradle)、[Wrapper](../../server/gradlew)、[依赖锁](../../server/app/gradle.lockfile) | JDK21 build实际通过；新JAR启动通过；Linux CI实际通过 |
| 统一DTO/错误/校验/分页/OpenAPI | [DTO](../../server/app/src/main/java/hy/CommunityBuy/dto/Dtos.java)、[统一异常](../../server/app/src/main/java/hy/CommunityBuy/exception/ApiErrors.java)、[OpenAPI](../../server/app/src/main/java/hy/CommunityBuy/infrastructure/OpenApiConfig.java) | [后端回归](../../server/app/src/test/java/hy/CommunityBuy/BackendIntegrationTest.java)正常/边界/异常/无泄密；Reliability审计DTO；管理员OpenAPI实际可读 |
| traceId与日志脱敏 | [trace](../../server/app/src/main/java/hy/CommunityBuy/infrastructure/TraceFilter.java)、[审计](../../server/app/src/main/java/hy/CommunityBuy/infrastructure/AuditAspect.java) | 伪造trace不反射、日志不含测试秘密、HTTP→审计一致；[恢复证据](evidence/deployment-release.json) |
| 认证与隐私 | [Security](../../server/app/src/main/java/hy/CommunityBuy/security/SecurityConfig.java)、[认证](../../server/app/src/main/java/hy/CommunityBuy/service/AuthService.java)、[用户](../../server/app/src/main/java/hy/CommunityBuy/service/UserService.java) | AuthPolicy1+Backend相关测试，伪造/缺声明/过期/注销/改密/封禁、越权、角色竞争、无密码；全部通过 |
| 库存事务与并发 | [商品](../../server/app/src/main/java/hy/CommunityBuy/service/ItemService.java)、[订单](../../server/app/src/main/java/hy/CommunityBuy/service/OrderService.java)、[明细](../../server/app/src/main/java/hy/CommunityBuy/commerce/OrderLines.java) | 最后一件、相同键、跨商品键、更新竞争、创建CHECK失败回滚、取消/付款/超时竞争；真实MySQL+CountDownLatch+可控Clock |
| 数据迁移/旧明文/历史身份 | [迁移V6](../../server/app/src/main/resources/db/migration/V6__audit_and_operational_indexes.sql)、[旧库迁移](../database/design.md) | [迁移3项](../../server/app/src/test/java/hy/CommunityBuy/MigrationTest.java)：空库、旧SQL和旧JPA宽度；原型baseline保留数据；不清库、不造历史归属 |
| 分类搜索/购物车/拆单 | [购物车结算](../../server/app/src/main/java/hy/CommunityBuy/commerce/ShopService.java)、[V4](../../server/app/src/main/resources/db/migration/V4__cart_checkout_order_lines.sql) | [购物车9项](../../server/app/src/test/java/hy/CommunityBuy/CommerceIntegrationTest.java)：多条件、分页权限、选中清理、价格/版本、跨卖家失败全回滚、并发重试/最后库存 |
| 支付记录/查询/通知 | [支付账簿](../../server/app/src/main/java/hy/CommunityBuy/finance/FinanceBook.java)、[渠道端口](../../server/app/src/main/java/hy/CommunityBuy/finance/PaymentChannel.java)、[通知签名](../../server/app/src/main/java/hy/CommunityBuy/finance/SimulatorSignatures.java) | [账务18项](../../server/app/src/test/java/hy/CommunityBuy/FinanceIntegrationTest.java)中验证验签/金额/商户/事件冲突/失败重试/主动查询/取消竞争；SIMULATOR通过，真实平台沙箱未实现/未验收 |
| 退款/评价/结算/统计 | [结算](../../server/app/src/main/java/hy/CommunityBuy/finance/FinanceService.java)、[退款](../../server/app/src/main/java/hy/CommunityBuy/service/OrderService.java)、[统计](../../server/app/src/main/java/hy/CommunityBuy/service/AdminService.java) | 全额审核重试、库存一次恢复、零价退款、评价唯一、结算竞争/调整、超Long平台金额精确；HTTP完整闭环通过 |
| 限流/CORS/监控/审计/输入安全 | [限流](../../server/app/src/main/java/hy/CommunityBuy/infrastructure/RequestLimitFilter.java)、[CORS](../../server/app/src/main/java/hy/CommunityBuy/security/CorsConfig.java)、[审计接口](../../server/app/src/main/java/hy/CommunityBuy/infrastructure/AuditController.java)、[运行说明](operations.md) | [可靠性8项](../../server/app/src/test/java/hy/CommunityBuy/ReliabilityIntegrationTest.java)全部通过；真实JAR health/metrics/trace可读，普通用户拒绝；纯文本拒绝及参数化搜索 |
| 依赖/静态检查/覆盖报告/CI | [构建检查](../../server/app/build.gradle)、[CI](../../.github/workflows/backend.yml) | 本地build与格式检查通过；JaCoCo生成；最终Linux Actions39261266487实际78项成功，clean无缓存；见ci-final.json |
| 性能目标与测量 | [压测脚本](../../server/scripts/load-test.py)、[预设目标](performance-plan.md) | 原始[测量原文](evidence/performance-release.json)：预设P95≤250ms/≥30rps/0错误；本地实测20.69ms/620.17rps/0，单读取样本，不推断生产容量 |
| 容器/代理/HTTPS | [Dockerfile](../../server/Dockerfile)、[Compose](../../server/compose.yml)、[Nginx](../../server/deploy/nginx.conf) | 本地无Docker，Linux CI39257549748已实际构建运行应用/代理并跑通完整流程，nginx -t与UID10001通过；公网HTTPS无证书仍待部署者验收 |
| 新环境、备份恢复、兼容回退 | [复制脚本](../../server/scripts/database-copy.py)、[部署步骤](deployment.md) | 原生MySQL8.4.6实际演练，见[恢复证据](evidence/deployment-release.json)；恢复有3订单/2支付/1退款/3结算批次，金额997/498/499，非空目标拒绝 |
| AI开发过程与协作 | [AI记录](ai-development-log.md)、[进度/提交](progress.md) | 真实Prompt来源摘要、生成/采纳/修正/验证记录；没有可确认的外部人工评审，缺失历史明确标注 |

## 当前测试与结果

本地命令（在server；无Docker测试环境变量见README）：`./gradlew formatJava build`（修改代码后）或`./gradlew clean build --no-daemon`，Windows对应gradlew.bat；随后`python3 scripts/check-tests.py`。最终代码本地78项，0失败/错误/跳过：AuthPolicy1、Backend39、Commerce9、Finance18、Migration3、Reliability8。测试使用原生MySQL8.4.6任务专用库；不使用H2，不用Mock替代库存/锁/迁移。故障注入仅渠道端口或数据库临时CHECK，时间用Clock、竞争用latch，无sleep制造并发。

当前JaCoCo实际LINE1203覆盖/117未覆盖（91.14%）、BRANCH471/164（74.17%）。未覆盖含bootstrap配置分支、禁用渠道、审计存储双重失败告警、部分防御性配置/容量边界及框架生成代码。核心成功、拒绝、回滚、并发行为有专项断言；不把覆盖率解释为生产安全保证。报告在app/build/reports/jacoco/test/html/index.html，测试HTML/XML在app/build/reports/tests/test和app/build/test-results/test。

运行命令与逐步演示、备份、恢复、回退均见[deployment.md](deployment.md)。本轮真实JAR演示不仅是MockMvc：购物车→两卖家拆单→模拟支付→发货收货→评价→内部结算→全额售后→负数调整，以及403/取消只回补一次/注销401。恢复前后全部表行数、订单明细合计和支付/退款/结算金额比较一致，V6兼容上一JAR启动后数据不变；不声称旧单商品二进制可回退。

## 明确未完成与风险

- 真实支付平台沙箱无凭据，当前PaymentChannel只有隔离SIMULATOR/禁用实现。真实网络超时、外部资金晚到自动退款、真实通知与银行结算均未验收。模拟晚到通知409待对账，不能复活订单；deploy禁用模拟。
- 售后仅每单一次全额申请；拒绝后不重开，不做部分退款、退货物流、多地址簿、短信/邮件找回、附件上传或实际银行打款。前端尚未联调且没有修改，适配见design-api.md。
- 限流是单实例固定分钟窗口，重启清零；账务单行锁适合课程规模，不承诺高并发写容量。监控令牌需轮换，未部署长期Prometheus/Grafana；吊销/审计归档待部署者配置。
- 本地没有Docker/公网域名证书；容器/代理已由远程CI验证，公网TLS、云MySQL和真实共享历史数据升级仍需维护者部署演练。逻辑备份仅本项目全InnoDB结构，执行期间禁止DDL。
- 团队库创建Draft PR返回GitHub集成403 Resource not accessible by integration。个人fork Draft PR #1可评审且不自动合并；它不替代团队PR。团队维护者可从fork分支手工创建上游Draft PR，或导入交付bundle。外部人工评审未进行。

提交阶段记录：bf3f19d/861864f基础，21e210c安全，21cda73快照，469c7ee购物车拆单，fa4cb36账务，8243529可靠性；后续部署与最终证据提交见progress.md和git log。通过GitHub git-data上传时逐个核对远端tree与本地一致；远端提交ID因父链元数据不同，映射留在交付报告。

## Linux与最终JAR证据补记

[Actions39257549748](https://github.com/Alpha234-s/CommuniBuy-by-ai/actions/runs/39257549748)成功，head035c21c与本地9191bfa树一致。记录见evidence/ci-container.json；不是本机执行Docker的声明。上轮Linux读取P95 53.07ms/201.59rps/0错误，也达到相同预设目标。最终本地JAR(evidence/performance-final.json)读取P95 22.44ms/616.69rps/0错误，与部署恢复证据JAR哈希一致。

最终自审修复Gradle外部数据库测试缓存问题：所有Test不允许缓存/up-to-date复用，CI clean build --no-build-cache。修改后本地build与输入不变第二次:app:test均实际执行77项并全通过。最终CI见evidence/ci-final.json，旧head结果与最后78项运行分开记录。

## 发布前审计修复（本地与Linux CI均通过）

新增真实MySQL1062唯一冲突HTTP回归，证实客户端409而审计500，修正AuditAspect使DataIntegrityViolationException/ConcurrencyFailureException与全局409一致。原CHECK回滚测试未改断言；最初误用3819/HY000样例的判断与失败留痕见evidence/audit-regression-before.json，未将它伪装为原本409。最终本地78项（Reliability增至8）0失败/错误/跳过，格式/构建成功；不删除或放宽原77项。

最新JAR SHA256对应evidence/performance-release.json、deployment-release.json：完整HTTP含10000成功/10001拒绝、权限/退款账务、备份恢复与兼容V6回退再次通过；读取P95 20.69ms/620.17rps/0错误，保留所有早期测量不覆盖。旧ci-proxy-boundary.json证明128k代理边界在JAR与Nginx后各运行一次；最后审计修复的78项CI已通过，记录ci-final.json，不把旧77测试称为新78运行。
