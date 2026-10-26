import { beforeEach, describe, expect, it, vi } from 'vitest'
import { finishIntent, getIntent, saveIntent } from '../../src/utils/intent'
import { encodeJson } from '../../src/utils/json'
beforeEach(() => {
  const values = new Map<string, string>()
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  })
})
describe('purchase intent recovery', () => {
  it('retains the original checkout version and key across a lost response', () => {
    const payload = encodeJson({
      address: 'test address',
      cartVersion: 9007199254740993n,
      expectedTotal: 123n,
    })
    const first = saveIntent('buyer', 'checkout', payload)
    expect(getIntent('buyer', 'checkout')).toEqual(first)
    expect(saveIntent('buyer', 'checkout', payload).key).toBe(first.key)
    expect(() =>
      saveIntent(
        'buyer',
        'checkout',
        encodeJson({ address: 'test address', cartVersion: 9007199254740994n, expectedTotal: 123n })
      )
    ).toThrow()
    expect(getIntent('otherBuyer', 'checkout')).toBeNull()
    finishIntent('buyer', 'checkout')
    expect(saveIntent('buyer', 'checkout', payload).key).not.toBe(first.key)
  })
})
