package hy.CommunityBuy.finance;

/** External channel port. Simulator is isolated; no real platform sandbox is claimed. */
public interface PaymentChannel {
  record Receipt(String status, String reference, Long amount, String merchant) {}

  String name();

  Receipt charge(String id, long amount);

  Receipt refund(String id, long amount);

  Receipt query(String kind, String id);
}
