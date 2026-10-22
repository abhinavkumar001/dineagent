import { api } from "./api.js";

let loading = null;

/** Loads Razorpay's checkout script once. */
function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve();
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const s = document.createElement("script");
      s.src = "https://checkout.razorpay.com/v1/checkout.js";
      s.onload = resolve;
      s.onerror = () => { loading = null; reject(new Error("Couldn’t load Razorpay. Check your internet connection.")); };
      document.body.appendChild(s);
    });
  }
  return loading;
}

/**
 * 1. backend creates a Razorpay order for the cart total (amount decided by the server)
 * 2. Razorpay Checkout opens; the customer pays (test mode: use Razorpay's test card or UPI id)
 * 3. backend verifies the signature and places the food order
 * Resolves with the placed order, rejects on failure or if the customer closes the popup.
 */
export async function payWithRazorpay(sessionId) {
  const [payment] = await Promise.all([api.createPayment(sessionId), loadRazorpay()]);
  return new Promise((resolve, reject) => {
    const checkout = new window.Razorpay({
      key: payment.keyId,
      amount: payment.amount,
      currency: payment.currency,
      order_id: payment.razorpayOrderId,
      name: "DineAgent",
      description: "Food order",
      theme: { color: "#1f6b43" },
      handler: async (res) => {
        try {
          resolve(await api.verifyPayment({
            sessionId,
            razorpayOrderId: res.razorpay_order_id,
            razorpayPaymentId: res.razorpay_payment_id,
            razorpaySignature: res.razorpay_signature,
          }));
        } catch (e) {
          reject(e);
        }
      },
      modal: { ondismiss: () => reject(new Error("Payment cancelled")) },
    });
    checkout.on("payment.failed", (r) => reject(new Error(r.error?.description || "Payment failed")));
    checkout.open();
  });
}
