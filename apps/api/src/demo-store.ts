import {
  demoDashboard,
  demoExperiences,
  demoOrganization,
  demoOrders,
  demoProducts,
  demoSmartLinks,
  type Experience,
} from "@snapplay/contracts";

export const demoStore = {
  organization: structuredClone(demoOrganization),
  dashboard: structuredClone(demoDashboard),
  products: structuredClone(demoProducts),
  experiences: structuredClone(demoExperiences),
  smartLinks: structuredClone(demoSmartLinks),
  orders: structuredClone(demoOrders),
};

export function addDemoExperience(experience: Experience): Experience {
  demoStore.experiences.unshift(experience);
  demoStore.dashboard.experiences = demoStore.experiences;
  return experience;
}
