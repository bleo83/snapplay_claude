import crypto from "node:crypto";
import { describe, expect, it } from "vitest";
import { verifyWebhookSignature } from "../src/webhook-security.js";

describe("webhook signature", () => {
  it("accepts a matching v1 signature inside the replay window", () => {
    const rawBody = Buffer.from('{"id":"evt_12345678"}');
    const timestamp = "1786896000";
    const secret = "a-development-secret-longer-than-32";
    const signature = `v1=${crypto.createHmac("sha256", secret).update(`${timestamp}.`).update(rawBody).digest("hex")}`;
    expect(
      verifyWebhookSignature({
        rawBody,
        timestamp,
        signature,
        secret,
        now: 1786896000 * 1000,
      }),
    ).toBe(true);
  });

  it("rejects stale and malformed signatures", () => {
    const input = {
      rawBody: Buffer.from("{}"),
      timestamp: "1786896000",
      signature: "v1=bad",
      secret: "a-development-secret-longer-than-32",
    };
    expect(verifyWebhookSignature({ ...input, now: 1786896000 * 1000 })).toBe(
      false,
    );
    expect(
      verifyWebhookSignature({
        ...input,
        signature: `v1=${"0".repeat(64)}`,
        now: 1786897000 * 1000,
      }),
    ).toBe(false);
  });
});
