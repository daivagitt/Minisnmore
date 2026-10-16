/** Copy selected files from a file input, then reset it (FileList is live and clears when value is reset). */
export function snapshotInputFiles(input: HTMLInputElement): File[] {
  const files = Array.from(input.files ?? []);
  input.value = "";
  return files;
}
