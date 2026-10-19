# 预先设定的非功能验收

本计划在本轮压测运行前写入。不能运行后上调目标或隐藏失败。

- 环境：独立本地JAR+MySQL8.4.6，不是共享/生产；硬件、操作系统、CPU逻辑核数、物理内存及JAR SHA256由脚本记录，无法获取的值记unavailable/null。
- 数据：创建独立普通账号作为团长，200个新商品；价格199分、库存1000；不删除已有数据。只按新账号筛选，报告不输出账号密码或token。
- 负载：20次预热，然后并发8、共400次GET分页查询，owner AND keyword AND status共同过滤，page0/size20/id DESC。
- 预设目标：成功请求P95≤250ms，整体吞吐≥30请求/秒，错误率0；每次同时校验count200和20条内容，不能将错误页计为成功。
- 记录P50/P95/P99、吞吐、错误率、持续时间、trace样本和硬件。一次读取测试不能代表生产容量或写入吞吐；不把MySQL不变量并发测试当吞吐压测。
- 写入一致性目标：不同买家争最后一件只有一个有效订单；同键并发只扣一次；跨卖家任一失败全回滚；退款/取消只回补一次；结算/退款净额守恒。由可控Clock/CountDownLatch的真实MySQL集成测试验证，不依靠sleep制造竞争。

在server目录、显式配置本地模拟环境并启动JAR后：

```bash
python3 scripts/load-test.py --output ../documents/server/evidence/performance.json --jar app/build/libs/communibuy.jar
```

脚本返回非零表示未达目标，保留原结果并分析，不篡改目标。HTTP读取未限流，写入初始化200次低于默认每用户300/分钟。登录和注册仍限流，反复运行过快可能触发429，应按Retry-After等待或在新测量前声明独立测试配置，不能悄悄关闭安全控制。
