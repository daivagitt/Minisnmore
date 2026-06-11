import { useEffect, useState } from "react";
import { getCachedBlogPosts, loadBlogPosts, subscribeBlogPosts } from "@/lib/storefront";
import { posts as seedPosts, type BlogPost } from "@/data/blog";

export function useBlogPosts(): BlogPost[] {
  const [list, setList] = useState<BlogPost[]>(getCachedBlogPosts() ?? seedPosts);
  useEffect(() => {
    let alive = true;
    const cached = getCachedBlogPosts();
    if (!cached) {
      loadBlogPosts()
        .then((p) => {
          if (alive) setList(p);
        })
        .catch((err) => {
          if (process.env.NODE_ENV !== "production") {
            console.error("useBlogPosts load failed", err);
          }
        });
    }
    const unsub = subscribeBlogPosts((p) => { if (alive) setList(p); });
    return () => { alive = false; unsub(); };
  }, []);
  return list;
}

export function useBlogPost(slug: string | undefined): { post: BlogPost | undefined; loading: boolean } {
  const all = useBlogPosts();
  const post = slug ? all.find((p) => p.slug === slug) : undefined;
  return { post, loading: all.length === 0 };
}
