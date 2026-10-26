import { describe, expect, it } from 'vitest'
import {
  money,
  priceToCents,
  saleState,
  safeReturnPath,
  validUserId,
  validatePassword,
} from '../../src/utils/format'
import { decodeJson, encodeJson, numericId, parseApiJson } from '../../src/utils/json'
import { orderPermissions } from '../../src/utils/permissions'
import type { Order, User } from '../../src/types/api'

describe('integer money and lossless JSON boundaries', () => {
  it('converts decimal input exactly, including zero and the INT limit', () => {
    expect(priceToCents('0.29')).toBe(29)
    expect(priceToCents('0')).toBe(0)
    expect(priceToCents('21474836.47')).toBe(2147483647)
    for (const text of ['0.001', '-1', '1e3', '', '21474836.48'])
      expect(() => priceToCents(text)).toThrow()
  })
  it('keeps Long IDs and arbitrarily large aggregate amounts before Number rounding', () => {
    const value = parseApiJson(
      '{"id":9223372036854775807,"total":9223372036854775807,"grossPaidAmount":999999999999999999999999999999,"quantity":3,"endTime":null}'
    )
    expect(value).toEqual({
      id: '9223372036854775807',
      total: '9223372036854775807',
      grossPaidAmount: '999999999999999999999999999999',
      quantity: 3,
      endTime: null,
    })
    const outgoing = encodeJson({
      itemId: numericId('9223372036854775807'),
      quantity: 3,
      expectedTotal: 9007199254740993n,
    })
    expect(outgoing).toContain('"itemId":9223372036854775807')
    expect(outgoing).toContain('"expectedTotal":9007199254740993')
    expect(decodeJson(outgoing)).toEqual({
      itemId: 9223372036854775807n,
      quantity: 3,
      expectedTotal: 9007199254740993n,
    })
  })
  it('formats negative settlement adjustments without floating point', () => {
    expect(money('-9007199254740993')).toBe('-90,071,992,547,409.93')
    expect(money('0')).toBe('0.00')
    expect(() => money(Number.MAX_SAFE_INTEGER + 1)).toThrow()
    expect(() => numericId('9223372036854775808')).toThrow()
    expect(() => numericId('../user')).toThrow()
  })
})

describe('auth and sale rules', () => {
  it('allows legacy short login passwords while validating new password bytes', () => {
    expect(validatePassword('abc', false)).toBe('')
    expect(validatePassword('abc')).not.toBe('')
    expect(validatePassword('中'.repeat(25))).toContain('72 字节')
    expect(validUserId('Buyer_1')).toBe(true)
    expect(validUserId('1buyer')).toBe(false)
  })
  it('keeps redirects local and excludes login loops', () => {
    expect(safeReturnPath('/orders/9')).toBe('/orders/9')
    for (const path of [
      'https://example.com',
      '//example.com',
      '/\\example.com',
      '/login?return=/login',
      '/register',
    ])
      expect(safeReturnPath(path)).toBe('/')
  })
  it('enforces the half-open purchase window and independent sale status', () => {
    const item = { status: 0, stock: 3, startTime: 100, endTime: 200 }
    expect(saleState(item, 99)).toBe('即将开始')
    expect(saleState(item, 100)).toBe('正在团购')
    expect(saleState(item, 200)).toBe('已结束')
    expect(saleState({ ...item, status: -1 }, 150)).toBe('已下架')
  })
  it('never gives admins buyer-only payment, refund or review actions', () => {
    const order = {
      user: 'buyer',
      owner: 'seller',
      status: 0,
      expiresAt: 500,
      refundedTotal: '0',
    } as Order
    const admin = { id: 'admin', role: 1 } as User
    const buyer = { id: 'buyer', role: 0 } as User
    expect(orderPermissions(order, admin, 100).pay).toBe(false)
    expect(orderPermissions(order, buyer, 500).pay).toBe(false)
    expect(orderPermissions({ ...order, status: 1 }, admin, 100).ship).toBe(true)
    expect(orderPermissions({ ...order, status: 3 }, admin, 100).review).toBe(false)
    expect(orderPermissions({ ...order, status: 3 }, buyer, 100).refund).toBe(true)
  })
})
