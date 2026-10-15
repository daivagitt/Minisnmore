# SSL certificate creation and usage

## Scope

Production HTTPS terminates at Nginx for:

- `minisnmore.com`
- `www.minisnmore.com`

The certificate is issued by Let’s Encrypt and managed by Certbot. Application services bind only to `127.0.0.1`; they do not load the public certificate themselves.

Expected certificate files:

```text
/etc/letsencrypt/live/minisnmore.com/fullchain.pem
/etc/letsencrypt/live/minisnmore.com/privkey.pem
```

The `live` paths are Certbot-managed symlinks. Do not copy, rename, or manually replace files under `/etc/letsencrypt/live`, `archive`, or `renewal`.

## Prerequisites

Before issuing a certificate:

1. DNS `A`/`AAAA` records for both names must resolve to this VPS. Remove a stale `AAAA` record if the VPS does not serve IPv6.
2. Inbound TCP ports 80 and 443 must be open.
3. Nginx must have a port-80 server block for both names.
4. No other process may occupy ports 80 or 443.
5. Run the commands as a sudo-capable server operator.

Read-only checks:

```bash
dig +short minisnmore.com A
dig +short www.minisnmore.com A
dig +short minisnmore.com AAAA
sudo ss -ltnp | grep -E ':(80|443)\b'
sudo nginx -t
```

## First certificate creation

Install Certbot and its Nginx plugin if they are not already installed:

```bash
sudo apt update
sudo apt install -y certbot python3-certbot-nginx
```

Take a recoverable copy of the active Nginx site before allowing Certbot to edit it:

```bash
sudo cp -a /etc/nginx/sites-available/mini-prod.conf \
  /etc/nginx/sites-available/mini-prod.conf.pre-certbot
```

Issue one certificate containing both names:

```bash
sudo certbot --nginx \
  --cert-name minisnmore.com \
  -d minisnmore.com \
  -d www.minisnmore.com
```

Choose HTTP-to-HTTPS redirect when prompted. Certbot should add or maintain these directives in the HTTPS server block:

```nginx
listen 443 ssl http2;
server_name minisnmore.com www.minisnmore.com;

ssl_certificate /etc/letsencrypt/live/minisnmore.com/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/minisnmore.com/privkey.pem;
include /etc/letsencrypt/options-ssl-nginx.conf;
ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;
```

The port-80 block should redirect both names to HTTPS. Do not expose ports `3000` or `9180–9184` publicly as a workaround.

## How the certificate is used

Nginx decrypts HTTPS and forwards HTTP to local services:

```text
Browser/provider HTTPS :443
        ↓ TLS terminates here
Nginx
        ├── frontend 127.0.0.1:3000
        ├── gateway  127.0.0.1:9180
        └── selected callbacks 127.0.0.1:9184
```

Every proxy location must preserve the original scheme so OAuth redirects, secure cookies, and generated links remain HTTPS:

```nginx
proxy_set_header Host $host;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto https;
proxy_set_header X-Forwarded-Port 443;
```

Certificate files contain private key material. Only Nginx/root should read the private key. Never commit or send `privkey.pem` through email or chat.

## Verification after issue or Nginx change

```bash
sudo nginx -t
sudo systemctl reload nginx
sudo certbot certificates
curl -fsSI https://minisnmore.com/
curl -fsSI https://www.minisnmore.com/
openssl s_client -connect minisnmore.com:443 -servername minisnmore.com </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates -ext subjectAltName
```

Acceptance criteria:

- Nginx configuration test passes.
- Both hostnames redirect or respond over HTTPS without a name warning.
- Subject Alternative Name contains both production hostnames.
- `notAfter` is in the future.
- `https://minisnmore.com/api/v1/categories` still returns JSON through the same TLS endpoint.

## Automatic renewal

Ubuntu normally runs Certbot through a systemd timer. Confirm it is enabled and test renewal without changing the certificate:

```bash
systemctl list-timers --all | grep certbot
sudo systemctl status certbot.timer
sudo certbot renew --dry-run
```

Certbot renews certificates near expiry and reloads Nginx when the Nginx installer is recorded. Verify `/etc/letsencrypt/renewal/minisnmore.com.conf` references the expected certificate and authenticator, but do not place secret values in this handover pack.

Monthly operational check:

```bash
sudo certbot certificates
sudo journalctl -u certbot --since '35 days ago' --no-pager
```

Set an external expiry alert at least 21 days before expiry; automation should not be the only warning.

## Adding or removing a hostname

Changing names reissues the certificate. Confirm DNS first, then specify the complete desired name set:

```bash
sudo certbot --nginx --cert-name minisnmore.com \
  -d minisnmore.com \
  -d www.minisnmore.com
```

Omitting an existing `-d` name can remove it from the renewed certificate. After a name change, also update Nginx `server_name`, DNS, CORS, OAuth redirect URLs, mail base URL, and any provider webhook URLs.

## Troubleshooting

| Symptom | Likely cause | Check / recovery |
| --- | --- | --- |
| `NET::ERR_CERT_COMMON_NAME_INVALID` | Requested hostname is not in SAN | Inspect with `openssl`; reissue with both `-d` names |
| Certbot HTTP-01 challenge fails | DNS points elsewhere, port 80 blocked, or wrong Nginx site | Check `dig`, firewall/security group, `nginx -T` |
| Old certificate still served | Another HTTPS server block or CDN edge is answering | Compare origin IP, `nginx -T`, and `openssl -servername` output |
| Renewal succeeds but Nginx serves old dates | Nginx was not reloaded | `sudo nginx -t && sudo systemctl reload nginx` |
| Nginx fails after certificate edit | Bad path or unreadable key | Restore the saved site file, run `nginx -t`, then reload |
| Browser loops between HTTP and HTTPS | Conflicting proxy/CDN SSL mode or redirects | Check the CDN mode and Nginx port-80/443 blocks |
| Webhooks/OAuth fail after HTTPS change | Wrong callback URL or forwarded scheme | Confirm provider URL and `X-Forwarded-Proto https` |

Useful logs:

```bash
sudo journalctl -u certbot -n 100 --no-pager
sudo journalctl -u nginx -n 100 --no-pager
sudo tail -n 100 /var/log/nginx/error.log
```

Do not use `--force-renewal` repeatedly: Let’s Encrypt rate limits apply. Use `--dry-run` for routine testing.

## Renewal incident procedure

If expiry is approaching and automatic renewal failed:

1. Confirm DNS and port 80 reach this server.
2. Run `sudo certbot renew --dry-run` and capture the exact failure.
3. Fix DNS, firewall, or Nginx validation errors.
4. Run `sudo certbot renew` once.
5. Run `sudo nginx -t && sudo systemctl reload nginx`.
6. Verify both names and the public API with the commands above.
7. Record the cause and restore the timer/alert so manual renewal is not required next time.

Revocation or private-key exposure is a security incident: issue a replacement key/certificate, revoke the affected certificate with Certbot, reload Nginx, and rotate any copied key material.
