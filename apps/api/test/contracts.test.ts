import { describe, expect, it } from "vitest";
import {
  dashboardSchema,
  demoDashboard,
  demoExperiences,
  demoOrders,
  demoProducts,
  demoSmartLinks,
  experienceSchema,
  orderSchema,
  catalogProductSchema,
  smartLinkSchema,
} from "@snapplay/contracts";

describe("demo fixtures", () => {
  it("match the public contracts", () => {
    expect(() => dashboardSchema.parse(demoDashboard)).not.toThrow();
    expect(() =>
      demoExperiences.forEach((item) => experienceSchema.parse(item)),
    ).not.toThrow();
    expect(() =>
      demoOrders.forEach((item) => orderSchema.parse(item)),
    ).not.toThrow();
    expect(() =>
      demoProducts.forEach((item) => catalogProductSchema.parse(item)),
    ).not.toThrow();
    expect(() =>
      demoSmartLinks.forEach((item) => smartLinkSchema.parse(item)),
    ).not.toThrow();
  });
});
