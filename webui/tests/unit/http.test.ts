import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, AxiosHeaders, type AxiosAdapter } from 'axios'
import { http, request, tokenStorage } from '../../src/api/http'
beforeEach(() => {
  const values = new Map<string, string>()
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  })
  vi.stubGlobal('window', new EventTarget())
})
describe('HTTP contract', () => {
  it('sends Bearer on GET /auth but not POST /auth and parses exact IDs', async () => {
    tokenStorage.set('test-token')
    const calls: { method: string | undefined; authorization: unknown }[] = []
    const adapter: AxiosAdapter = async (config) => {
      calls.push({ method: config.method, authorization: config.headers.get('Authorization') })
      return {
        config,
        status: 200,
        statusText: 'OK',
        headers: {},
        data: '{"success":true,"code":"OK","data":{"id":9223372036854775807},"traceId":"test"}',
      }
    }
    http.defaults.adapter = adapter
    expect(await request('GET', '/auth')).toEqual({ id: '9223372036854775807' })
    await request('POST', '/auth', { user: 'buyer', password: 'test-only' })
    expect(calls[0]?.authorization).toBe('Bearer test-token')
    expect(calls[1]?.authorization).toBeUndefined()
  })
  it('clears an expired token and emits auth invalidation', async () => {
    tokenStorage.set('expired-token')
    const expired = vi.fn()
    window.addEventListener('communibuy:unauthorized', expired)
    http.defaults.adapter = async (config) => {
      throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
        config,
        status: 401,
        statusText: 'Unauthorized',
        headers: {},
        data: '{"success":false,"message":"Authentication required","traceId":"trace1"}',
      })
    }
    await expect(request('GET', '/auth')).rejects.toMatchObject({ status: 401, traceId: 'trace1' })
    expect(tokenStorage.get()).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
  })
  it('honors Retry-After without sending another write', async () => {
    let count = 0
    http.defaults.adapter = async (config) => {
      count++
      throw new AxiosError('Rate limited', 'ERR_BAD_REQUEST', config, undefined, {
        config,
        status: 429,
        statusText: 'Too Many Requests',
        headers: AxiosHeaders.from({ 'retry-after': '30' }),
        data: '{"success":false,"message":"Rate limited","traceId":"trace2"}',
      })
    }
    await expect(request('POST', '/rate-test', {})).rejects.toMatchObject({ status: 429 })
    await expect(request('POST', '/rate-test', {})).rejects.toMatchObject({ status: 429 })
    expect(count).toBe(1)
  })
  it('reports a proxy HTML fallback as a contract mismatch', async () => {
    http.defaults.adapter = async (config) => ({
      config,
      status: 200,
      statusText: 'OK',
      headers: {},
      data: '<html>wrong server</html>',
    })
    await expect(request('GET', '/item')).rejects.toThrow('服务响应格式不匹配')
  })
})
