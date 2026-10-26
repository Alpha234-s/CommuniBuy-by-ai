import type { Order, User } from '../types/api'
export function orderPermissions(order: Order, user: User, now: number) {
  const buyer = user.id === order.user
  const seller = user.id === order.owner
  const admin = user.role === 1
  return {
    pay: buyer && order.status === 0 && now < order.expiresAt,
    ship: (seller || admin) && order.status === 1,
    receive: (buyer || admin) && order.status === 2,
    cancel: ((buyer || admin) && order.status === 0) || ((seller || admin) && order.status === 1),
    address: (buyer || admin) && order.status === 1,
    refund: buyer && [1, 2, 3].includes(order.status) && BigInt(order.refundedTotal) === 0n,
    review: buyer && order.status === 3 && BigInt(order.refundedTotal) === 0n,
  }
}
