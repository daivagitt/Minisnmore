import { useState } from "react";
import { Check, Copy } from "lucide-react";
import { copyText } from "@/lib/copy-text";
import { toast } from "sonner";

type Props = {
  value: string;
  label?: string;
  title?: string;
  successMessage?: string;
  className?: string;
  iconClassName?: string;
  variant?: "icon" | "text";
};

export function CopyButton({
  value,
  label = "Copy",
  title = "Copy",
  successMessage = "Copied",
  className = "",
  iconClassName = "h-3.5 w-3.5",
  variant = "icon",
}: Props) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    const ok = await copyText(value);
    if (!ok) {
      toast.error("Could not copy");
      return;
    }

    setCopied(true);
    toast.success(successMessage);
    window.setTimeout(() => setCopied(false), 2000);
  };

  if (variant === "text") {
    return (
      <button
        type="button"
        onClick={handleCopy}
        className={`inline-flex items-center gap-1.5 text-xs text-primary hover:underline ${className}`}
        aria-label={`${label} ${value}`}
        title={title}
      >
        {copied ? <Check className={iconClassName} /> : <Copy className={iconClassName} />}
        {copied ? "Copied" : label}
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={handleCopy}
      className={`inline-flex items-center justify-center rounded-full glass h-8 w-8 shrink-0 hover:bg-white/60 transition ${className}`}
      aria-label={`${label} ${value}`}
      title={title}
    >
      {copied ? <Check className={iconClassName} /> : <Copy className={iconClassName} />}
    </button>
  );
}
