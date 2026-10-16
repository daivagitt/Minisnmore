import { createFileRoute, Link } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { ChevronRight, ArrowLeft, Clock } from "lucide-react";
import { useBlogPost, useBlogPosts } from "@/hooks/useBlogPosts";

export const Route = createFileRoute("/blog/$slug")({
  head: ({ params }) => ({
    meta: [{ title: `${params.slug} · Mini's n More Blogs` }],
  }),
  component: BlogPost,
  notFoundComponent: () => (
    <div className="px-6 py-24 text-center">
      <div className="font-display text-3xl">Story not found</div>
      <Link to="/blog" className="story-link mt-4 inline-block">Back to blogs</Link>
    </div>
  ),
});

function BlogPost() {
  const { slug } = Route.useParams();
  const posts = useBlogPosts();
  const { post, loading } = useBlogPost(slug);
  if (loading) {
    return (
      <div className="px-6 py-24 text-center">
        <div className="font-display text-3xl">Loading…</div>
      </div>
    );
  }
  if (!post) {
    return (
      <div className="px-6 py-24 text-center">
        <div className="font-display text-3xl">Story not found</div>
        <Link to="/blog" className="story-link mt-4 inline-block">Back to blogs</Link>
      </div>
    );
  }
  const others = posts.filter((p) => p.slug !== post.slug).slice(0, 3);

  return (
    <div className="px-4 md:px-6 space-y-14">
      <div className="text-xs text-muted-foreground flex items-center gap-1.5">
        <Link to="/" className="hover:text-foreground">Home</Link><ChevronRight className="h-3 w-3" />
        <Link to="/blog" className="hover:text-foreground">Blogs</Link><ChevronRight className="h-3 w-3" />
        <span className="truncate max-w-[60vw]">{post.title}</span>
      </div>

      <motion.article
        initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}
        className="max-w-5xl mx-auto space-y-10">
        <div className="glass rounded-[2.5rem] overflow-hidden">
          <img
            src={post.cover}
            alt={post.title}
            className="w-full aspect-video md:aspect-21/9 object-cover"
          />
        </div>
        <header className="space-y-3 max-w-3xl mx-auto text-center">
          <div className="text-xs uppercase tracking-[0.32em] text-primary">{post.tag}</div>
          <h1 className="font-display text-3xl md:text-5xl leading-tight">{post.title}</h1>
          <div className="flex items-center justify-center gap-3 text-xs text-muted-foreground">
            <span>{post.date}</span><span>·</span>
            <span className="flex items-center gap-1"><Clock className="h-3 w-3" /> {post.readMins} min read</span>
          </div>
        </header>
        <div className="prose prose-lg max-w-3xl mx-auto text-foreground/85">
          <p className="text-lg md:text-xl text-muted-foreground italic">{post.excerpt}</p>
          {(Array.isArray(post.body) ? post.body : []).map((para, i) => (
            <p key={i} className="leading-relaxed">{para}</p>
          ))}
        </div>
        <div className="max-w-3xl mx-auto">
          <Link to="/blog" className="inline-flex items-center gap-2 text-sm text-primary">
            <ArrowLeft className="h-4 w-4" /> Back to blogs
          </Link>
        </div>
      </motion.article>

      <section className="space-y-6">
        <div className="flex items-end justify-between">
          <h2 className="font-display text-2xl md:text-3xl">More to read</h2>
          <Link to="/blog" className="text-sm text-primary story-link">All stories</Link>
        </div>
        <div className="grid md:grid-cols-3 gap-6">
          {others.map((p) => (
            <Link key={p.slug} to="/blog/$slug" params={{ slug: p.slug }} className="group glass rounded-[2rem] overflow-hidden block">
              <div className="aspect-5/4 overflow-hidden">
                <img src={p.cover} alt={p.title} className="h-full w-full object-cover group-hover:scale-[1.04] transition-transform duration-700" loading="lazy" />
              </div>
              <div className="p-5 space-y-2">
                <div className="text-[11px] uppercase tracking-[0.28em] text-primary">{p.tag}</div>
                <div className="font-display text-lg leading-snug">{p.title}</div>
                <p className="text-xs text-muted-foreground line-clamp-2">{p.excerpt}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}