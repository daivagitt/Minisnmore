import { resolveImageCacheLimits, type ImageCacheLimits } from "./config";

export type BlobCacheEntry = {
  objectUrl: string;
  bytes: number;
};

export type BlobCacheStats = {
  entries: number;
  bytes: number;
  maxEntries: number;
  maxBytes: number;
};

type LruNode = {
  key: string;
  entry: BlobCacheEntry;
  prev: LruNode | null;
  next: LruNode | null;
};

export class LruBlobCache {
  private readonly limits: ImageCacheLimits;
  private readonly map = new Map<string, LruNode>();
  private head: LruNode | null = null;
  private tail: LruNode | null = null;
  private totalBytes = 0;

  constructor(limits?: ImageCacheLimits) {
    this.limits = limits ?? resolveImageCacheLimits();
  }

  get(key: string): string | undefined {
    const node = this.map.get(key);
    if (!node) return undefined;
    this.moveToHead(node);
    return node.entry.objectUrl;
  }

  set(key: string, objectUrl: string, bytes: number): void {
    const existing = this.map.get(key);
    if (existing) {
      this.totalBytes -= existing.entry.bytes;
      existing.entry = { objectUrl, bytes };
      this.totalBytes += bytes;
      this.moveToHead(existing);
      this.evictWhileOverLimit();
      return;
    }

    const node: LruNode = {
      key,
      entry: { objectUrl, bytes },
      prev: null,
      next: null,
    };
    this.map.set(key, node);
    this.totalBytes += bytes;
    this.insertAtHead(node);
    this.evictWhileOverLimit();
  }

  delete(key: string): void {
    const node = this.map.get(key);
    if (!node) return;
    this.removeNode(node);
    this.map.delete(key);
    this.totalBytes -= node.entry.bytes;
    URL.revokeObjectURL(node.entry.objectUrl);
  }

  clear(): void {
    for (const node of this.map.values()) {
      URL.revokeObjectURL(node.entry.objectUrl);
    }
    this.map.clear();
    this.head = null;
    this.tail = null;
    this.totalBytes = 0;
  }

  stats(): BlobCacheStats {
    return {
      entries: this.map.size,
      bytes: this.totalBytes,
      maxEntries: this.limits.maxEntries,
      maxBytes: this.limits.maxBytes,
    };
  }

  private evictWhileOverLimit(): void {
    while (
      this.map.size > this.limits.maxEntries ||
      this.totalBytes > this.limits.maxBytes
    ) {
      if (!this.tail) break;
      this.delete(this.tail.key);
    }
  }

  private insertAtHead(node: LruNode): void {
    node.prev = null;
    node.next = this.head;
    if (this.head) this.head.prev = node;
    this.head = node;
    if (!this.tail) this.tail = node;
  }

  private moveToHead(node: LruNode): void {
    if (this.head === node) return;
    this.removeNode(node);
    this.insertAtHead(node);
  }

  private removeNode(node: LruNode): void {
    if (node.prev) node.prev.next = node.next;
    else this.head = node.next;
    if (node.next) node.next.prev = node.prev;
    else this.tail = node.prev;
    node.prev = null;
    node.next = null;
  }
}
