# shellcheck shell=bash
# Resolve the LIVE nginx vhost file for uat.minisnmore.com (never a .bak copy).

find_uat_nginx_site() {
  local candidate resolved

  for candidate in \
    /etc/nginx/sites-enabled/uat.minisnmore.com \
    /etc/nginx/sites-enabled/mini-frontend-uat \
    /etc/nginx/sites-available/uat.minisnmore.com \
    /etc/nginx/sites-available/mini-frontend-uat \
    /etc/nginx/conf.d/uat.minisnmore.com.conf; do
    if [ -L "$candidate" ] || [ -f "$candidate" ]; then
      resolved=$(readlink -f "$candidate" 2>/dev/null || echo "$candidate")
      if [[ "$resolved" != *".bak"* ]] && grep -q 'uat\.minisnmore\.com' "$resolved" 2>/dev/null; then
        echo "$resolved"
        return 0
      fi
    fi
  done

  if command -v nginx >/dev/null 2>&1; then
    candidate=$(
      nginx -T 2>/dev/null | awk '
        /# configuration file / { cfg=$4; sub(/:$/,"",cfg) }
        /server_name/ && /uat\.minisnmore\.com/ {
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
    if grep -q 'uat\.minisnmore\.com' "$candidate" 2>/dev/null; then
      echo "$candidate"
      return 0
    fi
  done < <(find /etc/nginx/sites-enabled /etc/nginx/sites-available /etc/nginx/conf.d -type f 2>/dev/null | sort)

  return 1
}
