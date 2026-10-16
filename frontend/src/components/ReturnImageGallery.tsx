type ReturnImageGalleryProps = {
  urls?: string[] | null;
  /** Tailwind height/width classes for thumbnails */
  thumbClassName?: string;
  alt?: string;
  className?: string;
};

export function ReturnImageGallery({
  urls,
  thumbClassName = "h-16 w-16",
  alt = "Uploaded return photo",
  className = "flex flex-wrap gap-2",
}: ReturnImageGalleryProps) {
  const imageUrls = urls ?? [];
  if (imageUrls.length === 0) {
    return null;
  }
  return (
    <div className={className}>
      {imageUrls.map((url) => (
        <a key={url} href={url} target="_blank" rel="noreferrer" className="shrink-0">
          <img
            src={url}
            alt={alt}
            referrerPolicy="no-referrer"
            className={`${thumbClassName} rounded-xl object-cover border border-border`}
          />
        </a>
      ))}
    </div>
  );
}
