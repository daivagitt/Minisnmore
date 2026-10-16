import { api } from "@/lib/api";
import { normalizeAddressPhone } from "@/lib/account.functions";
import { emailFieldError } from "@/lib/password-validation";

export type ContactMessagePayload = {
  name: string;
  email: string;
  phone: string;
  address: string;
  comment: string;
};

export type ContactMessageRow = ContactMessagePayload & {
  id?: string;
  created_date?: string | null;
};

type ContactMessageDto = {
  id?: number;
  name: string;
  email: string;
  phone: string;
  address: string;
  comment: string;
  createdDate?: string | null;
};

const INDIAN_MOBILE = /^[6-9]\d{9}$/;
const LIMITS = {
  name: 120,
  email: 180,
  phone: 10,
  address: 1000,
  comment: 4000,
} as const;

export function getContactMessageValidationErrors(row: ContactMessagePayload): string[] {
  const errors: string[] = [];
  if (!row.name.trim()) errors.push("Name is required.");
  else if (row.name.trim().length > LIMITS.name) errors.push("Name is too long.");
  const emailError = emailFieldError(row.email);
  if (emailError) errors.push(emailError);
  else if (row.email.trim().length > LIMITS.email) errors.push("Email is too long.");
  const phone = normalizeAddressPhone(row.phone);
  if (!INDIAN_MOBILE.test(phone)) errors.push("Enter a valid 10-digit Indian mobile number.");
  if (row.address.trim().length < 4) errors.push("Address is required.");
  else if (row.address.trim().length > LIMITS.address) errors.push("Address is too long.");
  if (row.comment.trim().length < 4) errors.push("Please enter your comments.");
  else if (row.comment.trim().length > LIMITS.comment) errors.push("Comments are too long.");
  return errors;
}

export async function submitContactMessage(row: ContactMessagePayload): Promise<ContactMessageRow> {
  const payload: ContactMessagePayload = {
    name: row.name.trim(),
    email: row.email.trim(),
    phone: normalizeAddressPhone(row.phone),
    address: row.address.trim(),
    comment: row.comment.trim(),
  };
  const errors = getContactMessageValidationErrors(payload);
  if (errors.length) throw new Error(errors.join("\n"));
  const dto = await api.post<ContactMessageDto>("/api/v1/contact-messages", payload);
  return fromContactMessageDto(dto);
}

export async function listAdminContactMessages(): Promise<ContactMessageRow[]> {
  const rows = await api.get<ContactMessageDto[]>("/api/v1/admin/contact-messages", { auth: true });
  return (Array.isArray(rows) ? rows : []).map(fromContactMessageDto);
}

export async function deleteAdminContactMessage(id: string) {
  return api.delete<{ deleted: boolean }>(`/api/v1/admin/contact-messages/${id}`, { auth: true });
}

function fromContactMessageDto(dto: ContactMessageDto): ContactMessageRow {
  return {
    id: dto.id == null ? undefined : String(dto.id),
    name: dto.name,
    email: dto.email,
    phone: dto.phone,
    address: dto.address,
    comment: dto.comment,
    created_date: dto.createdDate ?? null,
  };
}
