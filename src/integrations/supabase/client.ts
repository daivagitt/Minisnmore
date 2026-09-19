// Optional Supabase browser client (legacy). Production uses Spring Boot APIs only.
import { createClient } from '@supabase/supabase-js';
import type { Database } from './types';

export function isSupabaseConfigured(): boolean {
  const url = import.meta.env.VITE_SUPABASE_URL || process.env.SUPABASE_URL;
  const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY || process.env.SUPABASE_PUBLISHABLE_KEY;
  return !!(url && key);
}

function createSupabaseClient() {
  const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL || process.env.SUPABASE_URL;
  const SUPABASE_PUBLISHABLE_KEY =
    import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY || process.env.SUPABASE_PUBLISHABLE_KEY;

  if (!SUPABASE_URL || !SUPABASE_PUBLISHABLE_KEY) {
    // Don't throw during app startup when Supabase isn't configured.
    // This project can run against Spring Boot APIs only.
    return null;
  }

  return createClient<Database>(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, {
    auth: {
      storage: typeof window !== 'undefined' ? localStorage : undefined,
      persistSession: true,
      autoRefreshToken: true,
    },
  });
}

const SUPABASE_DISABLED_ERROR =
  'Supabase is disabled (missing env vars). This deployment uses the Spring Boot API instead.';

function createDisabledSupabaseProxy(path: string[] = []) {
  // Use a callable proxy so patterns like `supabase.auth.getSession()` fail safely.
  const dummy = function () { /* intentionally empty */ };

  return new Proxy(dummy as unknown as object, {
    get(_target, prop) {
      // Prevent `await supabase` / thenable detection from turning into confusing errors.
      if (prop === 'then') return undefined;
      return createDisabledSupabaseProxy([...path, String(prop)]);
    },
    apply() {
      const where = path.length ? path.join('.') : '<root>';
      return Promise.reject(new Error(`${SUPABASE_DISABLED_ERROR} Attempted: supabase.${where}`));
    },
  });
}

let _supabase: ReturnType<typeof createSupabaseClient> | null = null;

export const supabase = new Proxy({} as ReturnType<typeof createSupabaseClient>, {
  get(_, prop) {
    if (!isSupabaseConfigured()) {
      return createDisabledSupabaseProxy([String(prop)]);
    }

    if (!_supabase) _supabase = createSupabaseClient();
    if (!_supabase) return createDisabledSupabaseProxy([String(prop)]);

    const value = Reflect.get(_supabase as any, prop, _supabase as any);
    return typeof value === 'function' ? value.bind(_supabase) : value;
  },
});
