# Stock data proxy

GitHub Pages hosts static files and cannot forward the app's requests to Yahoo Finance, Nasdaq Trader, GitHub Raw, or Wikipedia. This Worker provides a small HTTPS proxy for only the paths the screener uses.

## Deploy

1. Create a Cloudflare account and an API token scoped to the account with permission to edit Cloudflare Workers.
2. In the GitHub repository settings, add these repository **secrets**:
   - `CLOUDFLARE_API_TOKEN`
   - `CLOUDFLARE_ACCOUNT_ID`
3. Run the **Deploy stock data proxy** workflow in GitHub Actions. It deploys the Worker as `stock-screener-data` on the account's `workers.dev` subdomain.
4. Copy the Worker URL shown in the workflow output. In repository settings, add a repository **variable** named `STOCK_DATA_PROXY_URL` with that HTTPS origin (for example, `https://stock-screener-data.<account-subdomain>.workers.dev`). Do not add a path suffix.
5. Run the **Build and deploy web app** workflow to publish a site build configured to use that Worker.

Without `STOCK_DATA_PROXY_URL`, GitHub Pages continues to serve the static app but cannot load live market data. The local development and Apache proxy paths remain available.
