import { parse, stringify } from 'lossless-json'
const strings = new Set([
  'id',
  'itemId',
  'orderId',
  'categoryId',
  'version',
  'cartVersion',
  'count',
  'price',
  'total',
  'amount',
  'netAmount',
  'refundedTotal',
  'userCount',
  'itemCount',
  'orderCount',
  'ongoingItemCount',
  'totalRevenue',
  'grossPaidAmount',
  'refundAmount',
  'netPaidAmount',
  'unreconciledPaidOrderCount',
])
function normalize(value: unknown, key = ''): unknown {
  if (value === null) return null
  if (strings.has(key) && (typeof value === 'number' || typeof value === 'bigint'))
    return value.toString()
  if (Array.isArray(value)) return value.map((v) => normalize(v))
  if (typeof value === 'object')
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([k, v]) => [k, normalize(v, k)])
    )
  return value
}
export function decodeJson(text: string): unknown {
  return parse(text, undefined, (source) => {
    const number = Number(source)
    return /^-?\d+$/.test(source) && !Number.isSafeInteger(number) ? BigInt(source) : number
  })
}
export function parseApiJson(text: string): unknown {
  return normalize(decodeJson(text))
}
export function encodeJson(value: unknown): string {
  return stringify(value) ?? 'null'
}
export function numericId(value: string): bigint {
  if (!/^[1-9]\d*$/.test(value) || BigInt(value) > 9223372036854775807n)
    throw new Error('资源编号无效')
  return BigInt(value)
}
