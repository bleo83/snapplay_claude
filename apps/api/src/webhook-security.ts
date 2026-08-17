import crypto from "node:crypto";

export function verifyWebhookSignature(input: {
  rawBody: Buffer;
  timestamp: string;
  signature: string;
  secret: string;
  now?: number;
}): boolean {
  const timestampSeconds = Number(input.timestamp);
  if (!Number.isInteger(timestampSeconds)) return false;
  const nowSeconds = Math.floor((input.now ?? Date.now()) / 1000);
  if (Math.abs(nowSeconds - timestampSeconds) > 300) return false;

  const [version, suppliedHex] = input.signature.split("=", 2);
  if (version !== "v1" || !suppliedHex || !/^[a-f0-9]{64}$/i.test(suppliedHex))
    return false;
  const expectedHex = crypto
    .createHmac("sha256", input.secret)
    .update(`${input.timestamp}.`)
    .update(input.rawBody)
    .digest("hex");
  return crypto.timingSafeEqual(
    Buffer.from(suppliedHex, "hex"),
    Buffer.from(expectedHex, "hex"),
  );
}
