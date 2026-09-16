import { useEffect, useState, type ComponentPropsWithoutRef } from "react";
import { adminImagePreviewSrc } from "@/lib/admin-image-preview";

export type AdminProductImagePriority = "high" | "low";

export type AdminProductImageProps = Omit<ComponentPropsWithoutRef<"img">, "src" | "loading" | "decoding"> & {
  src: string;
  priority?: AdminProductImagePriority;
};

export function AdminProductImage({
  src,
  alt = "",
  priority = "low",
  className,
  onError,
  fetchPriority,
  ...rest
}: AdminProductImageProps) {
  const original = (src ?? "").trim();
  const preview = adminImagePreviewSrc(original);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [original]);

  const display = !failed && preview ? preview : original;
  if (!display) return null;

  return (
    <img
      {...rest}
      src={display}
      alt={alt}
      decoding="async"
      loading={priority === "high" ? "eager" : "lazy"}
      fetchPriority={fetchPriority ?? (priority === "high" ? "high" : "low")}
      onError={(event) => {
        if (!failed && preview && preview !== original) {
          setFailed(true);
        }
        onError?.(event);
      }}
      className={className}
    />
  );
}
