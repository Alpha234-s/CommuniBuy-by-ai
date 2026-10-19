# 后端 API 契约（当前实现）

根路径 `/api/v1`。请求、响应 JSON；金额为整数分，时间为 UTC Unix 秒。所有接口（登录和公开注册除外）必须发送 `Authorization: Bearer <JWT>`。旧 `token` 请求头和请求体中的 token 不再兼容，避免多种认证入口产生歧义。

响应统一 `{"success":true,"message":"","data":...}`；失败为 `success:false,data:null` 和安全的 message。HTTP 400 输入错误、401 未认证/令牌无效/账号封禁、403 无权、404 不存在、409 状态/库存/幂等/数据库冲突、405 不支持的方法、500 未预期错误。不会返回 SQL、堆栈、密码或哈希。

ID：资源 ID 为正 Long，用户 ID 为 1..32 位 ASCII 字母/数字/下划线，首位字母或下划线，**区分大小写**。新约定允许语言关键字，不再采用无法跨语言维护的保留字黑名单。密码 8..72 字符，且不超过 72 UTF-8 字节；旧账号登录允许短旧密码，改密必须遵守新规则。未知 JSON 字段、浮点数量会 400。

分页统一 `page=0,size=20`，page 0..1000000、size 1..100；没有 size=0 全量模式。列表返回 `data:{content:[],count,page,size}`，数据库分页；用户按 ID 升序，商品/订单按 ID 降序稳定排序。多个筛选条件使用 AND，权限范围始终参与查询。

## 账号

| 方法与路径 | 请求 | 权限/响应 data |
| --- | --- | --- |
| POST `/auth/register` | `{user,password}` | 公开创建普通用户，重复 ID 409；UserView |
| POST `/auth` | `{user,password}` | 登录；`{token,tokenType:"Bearer",expiresAt}` |
| GET `/auth` | 无 | 当前 UserView |
| POST `/auth/logout` | 无请求体 | 吊销当前 jti；null，随后使用该令牌 401 |
| POST `/auth/reset` | `{oldPassword,newPassword}` | 自己改密，所有旧会话立即失效，重新登录 |
| GET `/user` | page,size | 仅管理员；分页用户 ID 列表 |
| POST `/user` | `{user,password}` | 仅管理员创建普通用户；UserView |
| GET `/user/{id}` | 无 | 本人或管理员可看完整资料 |
| POST `/user/{id}` | `{nickname?,description?,address?}` | 本人或管理员；至少一项，不能含 role/password |
| POST `/user/{id}/role` | `{role:-1或0或1}` | 仅管理员管理其他用户，禁止修改自己 |
| POST `/user/{id}/ban` | 无 | 同上，设 -1，并吊销其全部会话 |

UserView 仅 `{id,nickname,description,address,role}`，role=-1 封禁/0 普通/1 管理。角色变化增加会话版本；解封也不会恢复旧令牌。nickname 1..64、description 0..255、address 1..255，昵称和地址不能纯空白。地址是单字符串，未实现多个地址簿对象。空 `{}` 不删除资源，返回 400。

## 商品/团购

| 方法与路径 | 请求 | 说明 |
| --- | --- | --- |
| GET `/item` | page,size,keyword?,owner?,status? | 名称/描述包含关键字，按发起人和状态共同筛选；分页 ItemView |
| GET `/item/{id}` | 无 | 任意已认证用户 |
| POST `/item` | `{name,description?,price,stock,status?,startTime?,endTime?}` | 发起人来自当前用户，返回 ItemView |
| POST `/item/{id}` | 上述可选字段及 `version?` | 仅发起人/管理员，至少一项业务变更 |

name 非空且不超过128，description 最多10000，price 0..2147483647，stock 同范围。免费商品合法。status **仅 -1 下架、0 上架**，不再编码发货次数。响应 `shipmentCount` 独立统计已执行的订单发货次数。

startTime 默认服务端现在且 >=0；endTime 为 null 时无限，更新时 -1 表示清除截止时间，给定截止时间必须大于开始时间。购买窗口 `[startTime,endTime)`。

ItemView：`{id,name,description,price,stock,status,owner,startTime,endTime,shipmentCount,version}`。修改绝对剩余库存必须发送**最近一次读取的 version**；版本陈旧或缺失为409，前端刷新后重新决定库存。下单、回补、发货和商品更新都会更新版本。剩余库存加尚可取消的预留库存不得超出 INT 容量。下架不自动取消已存在订单。商品不物理删除，显式修改 `status:-1` 关闭销售。

## 订单

| 方法与路径 | 请求 | 说明 |
| --- | --- | --- |
| GET `/order` | page,size,user?,owner?,item?,status? | 买家/卖家只能查与自己有关的订单，管理员全局 |
| GET `/order/{id}` | 无 | 买家/卖家/管理员 |
| POST `/order` | `{itemId,quantity,address}` + `Idempotency-Key` 请求头 | 创建待支付订单；`item` 为 itemId 的兼容别名，不要同时发送两者 |
| POST `/order/{id}/pay` | 无请求体 | 仅买家模拟支付，待支付 → 待发货 |
| POST `/order/{id}/ship` | 无请求体 | 卖家/管理员，待发货 → 待收货 |
| POST `/order/{id}/receive` | 无请求体 | 买家/管理员，待收货 → 已完成 |
| POST `/order/{id}/cancel` | 无请求体 | 买家/管理员取消待支付；卖家/管理员取消待发货并模拟退款 |
| POST `/order/{id}/address` | `{address}` | 买家/管理员，只能待发货时改地址 |
| POST `/order/{id}` | `{address}` | 上一接口的兼容路径；禁止通用 status/时间更新 |

quantity 必须1..2147483647整数；address 非空白且最多255。买卖双方不能相同，买家/卖家不能封禁，商品必须可售且库存足够。卖家、买家、价格、total、状态、全部时间由后端确定，发送这些多余字段为400。

幂等键：1..64位字母、数字、下划线或短横线，按买家隔离，数据库唯一 `(user_id,idempotency_key)`。相同键及完全相同的 itemId、quantity、address 返回原订单的**当前结果**（包括已取消）；不同参数409。键在交易保留期内不删除，重试不再次扣库存。一个新购买意图应使用新键。

OrderView：`{id,itemId,user,owner,quantity,price,total,address,status,createTime,payTime,shipTime,receiptTime,closeTime,expiresAt,refundStatus}`。单价和total是下单快照；total 为64位整数。未付款时，卖家的响应 address 为null；买家和管理员可看。支付后卖家取得履约地址，其他用户不能查看订单。卖家不会取得买家的用户资料地址。

状态：0待支付、1待发货、2待收货、3完成、-1取消。唯一正常链为 `0→1→2→3`；允许 `0→-1` 和 `1→-1`（后一种由卖家/管理员操作，`refundStatus=SIMULATED_REFUNDED`），其余拒绝。支付/发货/收货在其目标状态重复请求返回原结果；取消在-1重复返回原结果；跨越后续状态的旧动作409。每次仍检查操作者，不能用重复动作绕过权限。

创建时间、截止时间、支付/发货/收货/关闭时间全部由后端写入。到截止秒即禁止支付，定时任务关闭并只回补一次；管理员也不能强行支付。已发货不支持取消。完成及取消的记录保留，未提供删除端点。

## 平台统计

`GET /stats` 仅管理员。data 为 `{userCount,itemCount,orderCount,totalRevenue,ongoingItemCount}`。交易额口径：状态1/2/3订单的 total 总和，含已付款未完成，排除未支付、取消及模拟退款。不是平台利润/抽佣。进行中团购要求上架且 `start<=now<end`（end无限除外），不按发货次数判断。

## 前端必须适配的差异

1. 所有 token 改为 Bearer 请求头，401清理登录态；登录从 `data.token` 取令牌。响应统一取 data，不能再读顶层 item/order/items/count/token。
2. 商品/订单创建返回完整 DTO，ID 分别为 `data.id`；列表是 `data.content` 对象数组，用户列表是字符串数组。订单输出字段统一 itemId（输入兼容 item）；价格从商品展示，不能提交买家/卖家/价格。
3. 更新商品库存发送 version。status 的正数旧含义移到 shipmentCount，展示发货计数应读新字段。
4. 下单生成并保存 Idempotency-Key，网络重试沿用。支付/发货/收货/取消调用显式操作路径；地址更新仅发送 address。
5. 不再使用空请求隐式删除；不提供订单/账号/商品物理删除。封禁和下架分别调用显式接口。size=0 调用需改为分页。
6. 本次不重写前端；旧前端需完成这些改动才能联调。
