/** Occasions shown in the gift flow — milestone-focused, baby-first. */
export const GIFT_OCCASIONS = [
  "Naming Ceremony",
  "First Birthday",
  "Welcome Baby",
  "Annaprashan",
  "Baby Shower",
] as const;

export type GiftOccasion = (typeof GIFT_OCCASIONS)[number];

const RECIPIENT_NAME_MAX = 80;
const NAME_HAS_LETTER_RE = /\p{L}/u;

/** Recipient name is required so we can personalize the note card and shipping brief. */
export function validateRecipientName(value: string): string | null {
  const name = value.trim();
  if (!name) return "Recipient's name is required for gift personalization.";
  if (name.length < 2) return "Recipient's name must be at least 2 characters.";
  if (!NAME_HAS_LETTER_RE.test(name)) return "Recipient's name must contain at least one letter.";
  if (name.length > RECIPIENT_NAME_MAX) {
    return `Recipient's name must be ${RECIPIENT_NAME_MAX} characters or fewer.`;
  }
  return null;
}

export function validateGiftStep(
  step: number,
  state: { recipient: string; selectedIds: string[] },
): string | null {
  if (step === 1) return validateRecipientName(state.recipient);
  if (step === 3 && state.selectedIds.length === 0) return "Pick at least one gift to continue.";
  return null;
}
