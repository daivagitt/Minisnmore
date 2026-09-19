# shellcheck shell=bash
# Resolve the LIVE nginx vhost file for minisnmore.com (never a .bak copy).

find_prod_nginx_site() {
  local candidate resolved

  for candidate in \
    /etc/nginx/sites-enabled/mini-prod.conf \
    /etc/nginx/sites-enabled/minisnmore.com \
    /etc/nginx/sites-enabled/mini-uat.conf \
    /etc/nginx/sites-available/mini-prod.conf \
    /etc/nginx/sites-available/minisnmore.com \
    /etc/nginx/sites-available/mini-uat.conf \
    /etc/nginx/conf.d/minisnmore.com.conf; do
    if [ -L "$candidate" ] || [ -f "$candidate" ]; then
      resolved=$(readlink -f "$candidate" 2>/dev/null || echo "$candidate")
      if [[ "$resolved" != *".bak"* ]] && grep -qE 'server_name.*minisnmore\.com' "$resolved" 2>/dev/null; then
        echo "$resolved"
        return 0
      fi
    fi
  done

  if command -v nginx >/dev/null 2>&1; then
    candidate=$(
      nginx -T 2>/dev/null | awk '
        /# configuration file / { cfg=$4; sub(/:$/,"",cfg) }
        /server_name/ && /minisnmore\.com/ {
          if (cfg != "" && cfg !~ /\.bak/) { print cfg; exit }
        }
      ' || true
    )
    if [ -n "$candidate" ] && [ -f "$candidate" ] && [[ "$candidate" != *".bak"* ]]; then
      echo "$candidate"
      return 0
    fi
  fi

  while IFS= read -r candidate; do
    [[ "$candidate" == *".bak"* ]] && continue
    if grep -qE 'server_name.*minisnmore\.com' "$candidate" 2>/dev/null; then
      echo "$candidate"
      return 0
    fi
  done < <(find /etc/nginx/sites-enabled /etc/nginx/sites-available /etc/nginx/conf.d -type f 2>/dev/null | sort)

  return 1
}
