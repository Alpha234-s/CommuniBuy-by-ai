export interface Intent {
  key: string
  payload: string
}
export function intentKey(user: string, scope: string): string {
  return `communibuy.intent:${user}:${scope}`
}
export function getIntent(user: string, scope: string): Intent | null {
  const raw = sessionStorage.getItem(intentKey(user, scope))
  if (!raw) return null
  try {
    const parsed: unknown = JSON.parse(raw)
    return parsed &&
      typeof parsed === 'object' &&
      'key' in parsed &&
      'payload' in parsed &&
      typeof parsed.key === 'string' &&
      typeof parsed.payload === 'string'
      ? (parsed as Intent)
      : null
  } catch {
    return null
  }
}
export function saveIntent(user: string, scope: string, payload: string): Intent {
  const existing = getIntent(user, scope)
  if (existing) {
    if (existing.payload !== payload)
      throw new Error('上次提交结果尚未确认，请先使用原请求重试，或前往订单核对')
    return existing
  }
  const intent = { key: crypto.randomUUID(), payload }
  sessionStorage.setItem(intentKey(user, scope), JSON.stringify(intent))
  return intent
}
export function finishIntent(user: string, scope: string): void {
  sessionStorage.removeItem(intentKey(user, scope))
}
