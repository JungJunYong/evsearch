# EV Search Backend Deployment Guide

## Prerequisite DNS / Server
- Public domain: `evsearch.wiqio.com`
- Server: Ubuntu/CentOS or any Linux host with Docker and Nginx installed
- Port requirement:
  - **8084** must be open on the firewall for external HTTPS/HTTP proxy access
  - Internal port mapped to **8084:8084** by docker-compose

## Quick Start (Docker Compose)

```bash
cd bff
docker-compose up -d --build
```

Check logs:

```bash
docker logs -f evsearch-bff
```

Check health:

```bash
curl http://127.0.0.1:8084/health
```

## Production Setup with Nginx Reverse Proxy

Place the provided Nginx config on the host machine (e.g. `/etc/nginx/sites-available/evsearch_bff`) and enable it:

```nginx
server {
    listen 80;
    server_name evsearch.wiqio.com;

    location / {
        proxy_pass http://127.0.0.1:8084;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_cache_bypass $http_upgrade;
        proxy_read_timeout 90s;
    }
}
```

Then link and reload Nginx:

```bash
sudo ln -s /etc/nginx/sites-available/evsearch_bff /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

(Optional) Enable HTTPS with Certbot:

```bash
sudo certbot --nginx -d evsearch.wiqio.com
sudo systemctl reload nginx
```

Once DNS is pointing to your server and container is running, the API will be available at:

- `http://evsearch.wiqio.com/v1/stations`
- `http://evsearch.wiqio.com/health`

## Troubleshooting
- If the container fails, check port mapping with `docker ps`.
- Ensure `.env` file exists next to docker-compose.yml or set `env_file` accordingly.
- To disable KECO live API fallback, set `USE_LIVE_API=false` in the environment.
